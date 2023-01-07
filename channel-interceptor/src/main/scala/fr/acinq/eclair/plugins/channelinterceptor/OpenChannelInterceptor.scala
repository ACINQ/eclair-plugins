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
import akka.actor.typed.scaladsl.Behaviors
import fr.acinq.eclair.wire.protocol.{Error, OpenDualFundedChannel}
import fr.acinq.eclair.{AcceptOpenChannel, InterceptOpenChannelReceived, RejectOpenChannel}

/**
 * Intercept OpenChannel and OpenDualFundedChannel messages received by the node. Respond to the peer
 * that received the request with SpawnChannelNonInitiator to continue the open channel process,
 * optionally with modified local parameters, or fail the request by responding to the initiator
 * with an Error message.
 */

object OpenChannelInterceptor {

  def apply(): Behavior[InterceptOpenChannelReceived] = {
    Behaviors.setup {
      _ => new OpenChannelInterceptor().start()
    }
  }
}

private class OpenChannelInterceptor() {

  private def start(): Behavior[InterceptOpenChannelReceived] = {

    Behaviors.receiveMessage {
      o: InterceptOpenChannelReceived =>
        o.replyTo ! (o.open match {
          case Left(_) =>
            // example: accept all single funded open channel requests
            AcceptOpenChannel(o.temporaryChannelId, o.localParams, o.fundingAmount_opt)
          case Right(o: OpenDualFundedChannel) =>
            // example: fail all dual funded open channel requests
            RejectOpenChannel(o.temporaryChannelId, Error(o.temporaryChannelId, "dual funded channels are not supported"))
        })
        Behaviors.same
    }
  }

}
