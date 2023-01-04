/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.eclair.plugins.channelinterceptor

import akka.actor.typed.Behavior
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import fr.acinq.eclair.channel.ChannelConfig
import fr.acinq.eclair.io.OpenChannelReceived
import fr.acinq.eclair.io.Peer.{OutgoingMessage, SpawnChannelNonInitiator}
import fr.acinq.eclair.wire.protocol.{Error, OpenDualFundedChannel}

/**
 * Intercept OpenChannel and OpenDualFundedChannel messages received by the node. Respond to the peer
 * that received the request with SpawnChannelNonInitiator to continue the open channel process,
 * optionally with modified local parameters, or fail the request by responding to the initiator
 * with an Error message.
 */

object OpenChannelInterceptor {

  def apply(): Behavior[Command] = {
    Behaviors.setup {
      context => new OpenChannelInterceptor(context).start()
    }
  }

  // @formatter:off
  sealed trait Command
  // @formatter:on

  private case class WrappedOpenChannelReceived(openChannelReceived: OpenChannelReceived) extends Command

}

private class OpenChannelInterceptor(context: ActorContext[OpenChannelInterceptor.Command]) {

  import OpenChannelInterceptor._

  def start(): Behavior[Command] = {
    context.system.eventStream ! EventStream.Subscribe(context.messageAdapter[OpenChannelReceived](WrappedOpenChannelReceived))

    Behaviors.receiveMessage {
      case WrappedOpenChannelReceived(wo) =>
        wo.peer ! (wo.open match {
          case Left(_) =>
            // example: accept all single funded open channel requests
            SpawnChannelNonInitiator(wo.open, ChannelConfig.standard, wo.channelType, wo.localParams)
          case Right(o: OpenDualFundedChannel) =>
            // example: fail all dual funded open channel requests
            OutgoingMessage(Error(o.temporaryChannelId, "dual funded channel request rejected"), wo.connectionInfo.peerConnection)
        })
        Behaviors.same
    }
  }

}
