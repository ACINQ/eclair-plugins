/*
 * Copyright 2026 ACINQ SAS
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

package fr.acinq.eclair.plugins.utxovalidator

import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.adapter.TypedActorRefOps
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import fr.acinq.bitcoin.scalacompat.{Transaction, TxId}
import fr.acinq.eclair.blockchain.OnChainWallet
import fr.acinq.eclair.channel._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
 * Intercept channels that are being opened (or splice transactions being created) and force-close them immediately if
 * they use blacklisted UTXOs. This example plugin demonstrates how channels can be automatically closed before they
 * become usable: users should plug their own validation rules based on whatever blacklist they use.
 */
object UtxoValidator {
  // @formatter:off
  sealed trait Command
  case class WrappedChannelFundingCreated(e: ChannelFundingCreated) extends Command
  private case class WrappedChannelAborted(e: ChannelAborted) extends Command
  private case class WrappedChannelClosed(e: ChannelClosed) extends Command
  private case class FetchTransaction(txId: TxId) extends Command
  private case class TransactionNotFound(txId: TxId) extends Command
  private case class ValidateTransaction(tx: Transaction) extends Command
  // @formatter:on

  def apply(config: UtxoValidatorPluginConfig, wallet: OnChainWallet, register: ActorRef[Any]): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        context.system.eventStream ! EventStream.Subscribe(context.messageAdapter(WrappedChannelFundingCreated))
        context.system.eventStream ! EventStream.Subscribe(context.messageAdapter(WrappedChannelAborted))
        context.system.eventStream ! EventStream.Subscribe(context.messageAdapter(WrappedChannelClosed))
        new UtxoValidator(config, wallet, register, timers, context).listen(Map.empty)
      }
    }
  }

}

private class UtxoValidator(config: UtxoValidatorPluginConfig,
                            wallet: OnChainWallet,
                            register: ActorRef[Any],
                            timers: TimerScheduler[UtxoValidator.Command],
                            context: ActorContext[UtxoValidator.Command]) {

  import UtxoValidator._

  implicit val ec: ExecutionContext = context.system.executionContext
  private val log = context.log

  private def listen(pending: Map[TxId, ChannelFundingCreated]): Behavior[Command] = {
    Behaviors.receiveMessage {
      case WrappedChannelFundingCreated(e) =>
        if (pending.contains(e.fundingTxId)) {
          Behaviors.same
        } else {
          e.fundingTx match {
            case Right(fundingTx) => context.self ! ValidateTransaction(fundingTx)
            case Left(fundingTxId) => context.self ! FetchTransaction(fundingTxId)
          }
          listen(pending + (e.fundingTxId -> e))
        }
      case WrappedChannelAborted(e) =>
        val pending1 = pending.filter { case (_, p) => p.channelId != e.channelId }
        listen(pending1)
      case WrappedChannelClosed(e) =>
        val pending1 = pending.removedAll(e.commitments.all.map(_.fundingTxId))
        listen(pending1)
      case FetchTransaction(txId) =>
        if (pending.contains(txId)) {
          context.pipeToSelf(wallet.getTransaction(txId)) {
            case Failure(_) => TransactionNotFound(txId)
            case Success(fundingTx) => ValidateTransaction(fundingTx)
          }
        }
        Behaviors.same
      case TransactionNotFound(txId) =>
        log.debug("cannot find txId={}, retrying...", txId)
        timers.startSingleTimer(FetchTransaction(txId), delay = config.fetchTxFrequency)
        Behaviors.same
      case ValidateTransaction(tx) =>
        pending.get(tx.txid) match {
          case Some(e) if tx.txIn.exists(txIn => config.blacklistedUtxos.contains(txIn.outPoint)) =>
            log.warn("fundingTxId={} for channelId={} contains blacklisted utxos: closing channel", tx.txid, e.channelId)
            register ! Register.Forward(context.system.ignoreRef, e.channelId, CMD_FORCECLOSE(context.system.ignoreRef.toClassic))
          case _ => ()
        }
        listen(pending - tx.txid)
    }
  }

}
