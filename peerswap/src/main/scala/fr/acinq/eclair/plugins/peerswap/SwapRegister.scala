/*
 * Copyright 2022 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.plugins.peerswap

import akka.actor
import akka.actor.typed
import akka.actor.typed.ActorRef.ActorRefOps
import akka.actor.typed.scaladsl.adapter.TypedActorRefOps
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import fr.acinq.bitcoin.scalacompat.Crypto.PublicKey
import fr.acinq.bitcoin.scalacompat.Satoshi
import fr.acinq.eclair.blockchain.OnChainWallet
import fr.acinq.eclair.blockchain.bitcoind.ZmqWatcher
import fr.acinq.eclair.channel.{CMD_GET_CHANNEL_INFO, RES_GET_CHANNEL_INFO, Register}
import fr.acinq.eclair.io.Peer.RelayUnknownMessage
import fr.acinq.eclair.io.{Peer, Switchboard, UnknownMessageReceived}
import fr.acinq.eclair.plugins.peerswap.SwapCommands._
import fr.acinq.eclair.plugins.peerswap.SwapHelpers.makeUnknownMessage
import fr.acinq.eclair.plugins.peerswap.SwapRegister.Command
import fr.acinq.eclair.plugins.peerswap.SwapResponses._
import fr.acinq.eclair.plugins.peerswap.SwapRole.SwapRole
import fr.acinq.eclair.plugins.peerswap.db.SwapsDb
import fr.acinq.eclair.plugins.peerswap.wire.protocol.PeerSwapMessageCodecs.peerSwapMessageCodec
import fr.acinq.eclair.plugins.peerswap.wire.protocol.{CancelSwap, HasSwapId, SwapInRequest, SwapOutRequest, SwapRequest}
import fr.acinq.eclair.wire.protocol.LightningMessageCodecs.unknownMessageCodec
import fr.acinq.eclair.{NodeParams, ShortChannelId, randomBytes32}
import scodec.Attempt

import scala.reflect.ClassTag

object SwapRegister {
  // @formatter:off
  sealed trait Command
  sealed trait ReplyToMessages extends Command {
    def replyTo: ActorRef[Response]
  }

  sealed trait RegisteringMessages extends Command
  private case class ChannelInfoFailure(replyTo: ActorRef[Response], shortChannelId: ShortChannelId, failure: Register.ForwardShortIdFailure[CMD_GET_CHANNEL_INFO]) extends RegisteringMessages
  case class WrappedUnknownMessageReceived(message: UnknownMessageReceived) extends RegisteringMessages
  case class SwapRequested(replyTo: ActorRef[Response], role: SwapRole, amount: Satoshi, shortChannelId: ShortChannelId, remoteNodeId: Option[PublicKey], remotePeer: Option[actor.ActorRef]) extends RegisteringMessages
  private case class SwapTerminated(swapId: String) extends RegisteringMessages
  case class ListPendingSwaps(replyTo: ActorRef[Iterable[Status]]) extends RegisteringMessages
  case class CancelSwapRequested(replyTo: ActorRef[Response], swapId: String) extends RegisteringMessages with ReplyToMessages

  sealed trait RestoringMessages extends Command
  private case class PeerInfoFailure(replyTo: Option[ActorRef[Response]], scid: String, remoteNodeId: PublicKey) extends RestoringMessages with RegisteringMessages
  private case class RestoreSwapWithPeer(swapData: SwapData, remotePeer: actor.ActorRef) extends RestoringMessages with RegisteringMessages
  // @formatter:on

  def apply(nodeParams: NodeParams, paymentInitiator: actor.ActorRef, watcher: ActorRef[ZmqWatcher.Command], register: actor.ActorRef, switchboard: actor.ActorRef, wallet: OnChainWallet, keyManager: SwapKeyManager, db: SwapsDb, data: Seq[SwapData]): Behavior[Command] = Behaviors.setup { context =>
    new SwapRegister(context, nodeParams, paymentInitiator, watcher, register, switchboard, wallet, keyManager, db).restoring(data, Map())
  }
}

private class SwapRegister(context: ActorContext[Command], nodeParams: NodeParams, paymentInitiator: actor.ActorRef, watcher: ActorRef[ZmqWatcher.Command], register: actor.ActorRef, switchboard: actor.ActorRef, wallet: OnChainWallet, keyManager: SwapKeyManager, db: SwapsDb) {
  import SwapRegister._

  private case class SwapEntry(shortChannelId: String, swap: ActorRef[SwapCommands.SwapCommand])

  private def myReceive[B <: Command : ClassTag](stateName: String)(f: B => Behavior[Command]): Behavior[Command] =
    Behaviors.receiveMessage[Command] {
      case m: B => f(m)
      case m =>
        context.log.error(s"received unhandled message while in state $stateName of ${m.getClass.getSimpleName}")
        Behaviors.same
    }
  private def watchForUnknownMessage(watch: Boolean)(implicit context: ActorContext[Command]): Unit =
    if (watch) context.system.classicSystem.eventStream.subscribe(unknownMessageReceivedAdapter(context).toClassic, classOf[UnknownMessageReceived])
    else context.system.classicSystem.eventStream.unsubscribe(unknownMessageReceivedAdapter(context).toClassic, classOf[UnknownMessageReceived])
  private def unknownMessageReceivedAdapter(context: ActorContext[Command]): ActorRef[UnknownMessageReceived] = {
    context.messageAdapter[UnknownMessageReceived](WrappedUnknownMessageReceived)
  }

  private def spawnSwap(swapRole: SwapRole, remoteNodeId: PublicKey, remotePeer: actor.ActorRef, scid: String) = {
    swapRole match {
      // swap maker is safe to resume because an opening transaction will only be funded once
      case SwapRole.Maker => context.spawn(Behaviors.supervise(SwapMaker(remoteNodeId, nodeParams, watcher, remotePeer, wallet, keyManager, db)).onFailure(typed.SupervisorStrategy.resume), "SwapMaker-" + scid)
      // swap taker is safe to resume because a payment will only be sent once
      case SwapRole.Taker => context.spawn(Behaviors.supervise(SwapTaker(remoteNodeId, nodeParams, paymentInitiator, watcher, remotePeer, wallet, keyManager, db)).onFailure(typed.SupervisorStrategy.resume), "SwapTaker-" + scid)
    }
  }

  private def restoreSwap(checkPoint: SwapData, remotePeer: actor.ActorRef): (String, SwapEntry) = {
    val swap = spawnSwap(checkPoint.swapRole, checkPoint.remoteNodeId, remotePeer, checkPoint.scid)
    context.watchWith(swap, SwapTerminated(checkPoint.swapId))
    swap ! RestoreSwap(checkPoint)
    checkPoint.swapId -> SwapEntry(checkPoint.scid, swap.unsafeUpcast)
  }

  private def channelInfoResultAdapter(replyTo: ActorRef[Response], role: SwapRole, amount: Satoshi, shortChannelId: ShortChannelId): ActorRef[RES_GET_CHANNEL_INFO] =
    context.messageAdapter[RES_GET_CHANNEL_INFO](r =>
      SwapRequested(replyTo, role, amount, shortChannelId, Some(r.nodeId), None)
    )

  private def fillRemoteNodeId(replyTo: ActorRef[Response], role: SwapRole, amount: Satoshi, shortChannelId: ShortChannelId): Unit = {
    register ! Register.ForwardShortId(context.messageAdapter[Register.ForwardShortIdFailure[CMD_GET_CHANNEL_INFO]](f => ChannelInfoFailure(replyTo, shortChannelId, f)),
      shortChannelId, CMD_GET_CHANNEL_INFO(channelInfoResultAdapter(replyTo, role, amount, shortChannelId).toClassic))
  }

  private def fillRemotePeer(replyTo: ActorRef[Response], role: SwapRole, amount: Satoshi, shortChannelId: ShortChannelId, remoteNodeId: PublicKey): Unit = {
    switchboard ! Switchboard.GetPeerInfo(context.messageAdapter[Peer.PeerInfoResponse] {
      case f: Peer.PeerNotFound => PeerInfoFailure(Some(replyTo), shortChannelId.toCoordinatesString, f.nodeId)
      case i: Peer.PeerInfo => SwapRequested(replyTo, role, amount, shortChannelId, Some(remoteNodeId), Some(i.peer))
    }, remoteNodeId)
  }

  private def initiateSwap(replyTo: ActorRef[Response], amount: Satoshi, remoteNodeId: PublicKey, remotePeer: actor.ActorRef, shortChannelId: ShortChannelId, swapRole: SwapRole): (String, SwapEntry) = {
    // TODO: check that new random swapId does not already exist in db?
    val swapId = randomBytes32().toHex
    val swap = spawnSwap(swapRole, remoteNodeId, remotePeer, shortChannelId.toCoordinatesString)
    context.watchWith(swap, SwapTerminated(swapId))
    swapRole match {
      case SwapRole.Taker => swap ! StartSwapOutSender(amount, swapId, shortChannelId)
      case SwapRole.Maker => swap ! StartSwapInSender(amount, swapId, shortChannelId)
    }
    replyTo ! SwapOpened(swapId)
    swapId -> SwapEntry(shortChannelId.toCoordinatesString, swap.unsafeUpcast)
  }

  private def receiveSwap(remotePeer: actor.ActorRef, remoteNodeId: PublicKey, request: SwapRequest): (String, SwapEntry) = {
    val swap = spawnSwap(request match {
      case _: SwapInRequest => SwapRole.Taker
      case _: SwapOutRequest => SwapRole.Maker
    }, remoteNodeId, remotePeer, request.scid)

    context.watchWith(swap, SwapTerminated(request.swapId))
    request match {
      case r: SwapInRequest => swap ! StartSwapInReceiver(r)
      case r: SwapOutRequest => swap ! StartSwapOutReceiver(r)
    }
    request.swapId -> SwapEntry(request.scid, swap.unsafeUpcast)
  }

  private def cancelSwap(peer: actor.ActorRef, swapId: String, reason: String): Unit = {
    peer ! RelayUnknownMessage(makeUnknownMessage(CancelSwap(swapId, reason)))
  }

  private def restoring(data: Seq[SwapData], swaps: Map[String, SwapEntry]): Behavior[Command] = {
    data.headOption match {
      case Some(swapData) =>
        switchboard ! Switchboard.GetPeerInfo(context.messageAdapter[Peer.PeerInfoResponse] {
          case f: Peer.PeerNotFound => PeerInfoFailure(None, swapData.scid, f.nodeId)
          case i: Peer.PeerInfo => RestoreSwapWithPeer(swapData, i.peer)
        }, swapData.remoteNodeId)
        myReceive[RestoringMessages]("restoring") {
          case PeerInfoFailure(_, _, _) =>
            context.log.error(s"could not restore swap ${swapData.swapId} with peer ${swapData.remoteNodeId}: peer not found")
            restoring(data.tail, swaps)
          case RestoreSwapWithPeer(swapData, peer) =>
            val (swapId, swapEntry) = restoreSwap(swapData, peer)
            restoring(data.tail, swaps + (swapId -> swapEntry))
        }
      case None => registering(swaps)
    }
  }

  private def registering(swaps: Map[String, SwapEntry]): Behavior[Command] = {
    watchForUnknownMessage(watch = true)(context)
    myReceive[RegisteringMessages]("registering") {
      case SwapRequested(replyTo, _, _, shortChannelId, _, _) if swaps.exists(p => p._2.shortChannelId == shortChannelId.toCoordinatesString) =>
        replyTo ! SwapExistsForChannel(shortChannelId)
        Behaviors.same
      case SwapRequested(replyTo, role, amount, shortChannelId, None, _) =>
        fillRemoteNodeId(replyTo, role, amount, shortChannelId)
        Behaviors.same
      case SwapRequested(replyTo, role, amount, shortChannelId, Some(remoteNodeId), None) =>
        fillRemotePeer(replyTo, role, amount, shortChannelId, remoteNodeId)
        Behaviors.same
      case SwapRequested(replyTo, role, amount, shortChannelId, Some(remoteNodeId), Some(remotePeer)) =>
        registering(swaps + initiateSwap(replyTo, amount, remoteNodeId, remotePeer, shortChannelId, role))
      case ListPendingSwaps(replyTo: ActorRef[Iterable[Status]]) =>
        val aggregator = context.spawn(StatusAggregator(swaps.size, replyTo), s"status-aggregator")
        swaps.values.foreach(e => e.swap ! GetStatus(aggregator))
        Behaviors.same
      case CancelSwapRequested(replyTo: ActorRef[Response], swapId: String) =>
        swaps.get(swapId) match {
          case Some(e) => e.swap ! CancelRequested(replyTo)
          case None => replyTo ! SwapNotFound(swapId)
        }
        Behaviors.same
      case SwapTerminated(swapId) =>
        db.restore().find({ d => d.swapId == swapId }) match {
          case None => registering(swaps - swapId)
          case Some(d) =>
            context.log.error(s"Swap ${d.swapId} stopped prematurely after saving a checkpoint, but before recording a result.")
            // restore swap if stopped prematurely
            switchboard ! Switchboard.GetPeerInfo(context.messageAdapter[Peer.PeerInfoResponse] {
              case f: Peer.PeerNotFound => PeerInfoFailure(None, d.scid, f.nodeId)
              case i: Peer.PeerInfo => RestoreSwapWithPeer(d, i.peer)
            }, d.remoteNodeId)
            registering(swaps)
        }
      case RestoreSwapWithPeer(swapData, peer) =>
        val (swapId, swapEntry) = restoreSwap(swapData, peer)
        registering(swaps + (swapId -> swapEntry))
      case WrappedUnknownMessageReceived(unknownMessageReceived) =>
        if (PeerSwapPlugin.peerSwapTags.contains(unknownMessageReceived.message.tag)) {
          peerSwapMessageCodec.decode(unknownMessageCodec.encode(unknownMessageReceived.message).require) match {
            case Attempt.Successful(decodedMessage) => decodedMessage.value match {
              case swapRequest: SwapRequest if swaps.exists(s => s._2.shortChannelId == swapRequest.scid) =>
                context.log.info(s"ignoring swap request for a channel with an active swap: $swapRequest")
                cancelSwap(unknownMessageReceived.peer, swapRequest.swapId, "Swap already in progress.")
                Behaviors.same
              case swapRequest: SwapRequest if db.list().exists(s => s.swapId == swapRequest.swapId) =>
                context.log.error(s"ignoring swap request with a previously used swap id: $swapRequest")
                cancelSwap(unknownMessageReceived.peer, swapRequest.swapId, "Previously used swap id.")
                Behaviors.same
              case swapRequest: SwapRequest =>
                registering(swaps + receiveSwap(unknownMessageReceived.peer, unknownMessageReceived.nodeId, swapRequest))
              case msg: HasSwapId => swaps.get(msg.swapId) match {
                case Some(e) => e.swap ! SwapMessageReceived(msg)
                  Behaviors.same
                case None => context.log.error(s"received unhandled swap message: $msg")
                  Behaviors.same
              }
            }
            case _ => context.log.error(s"could not decode unknown message received: $unknownMessageReceived")
              Behaviors.same
          }
        } else {
          // unknown message received without a peerswap message tag
          Behaviors.same
        }
      case ChannelInfoFailure(replyTo, shortChannelId, failure) =>
        replyTo ! ChannelInfoNotFound(shortChannelId, failure)
        Behaviors.same
      case PeerInfoFailure(replyTo, scid, remoteNodeId) =>
        context.log.error(s"could not restore swap for channel $scid with peer $remoteNodeId: peer not found")
        if (replyTo.isDefined) replyTo.get ! PeerInfoNotFound(scid, remoteNodeId)
        Behaviors.same
    }
  }
}
