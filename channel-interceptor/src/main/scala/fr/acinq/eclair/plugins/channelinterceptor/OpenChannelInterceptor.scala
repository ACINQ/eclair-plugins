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

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import fr.acinq.bitcoin.scalacompat.Satoshi
import fr.acinq.eclair.router.Router
import fr.acinq.eclair.router.Router.{GetNode, PublicNode, UnknownNode}
import fr.acinq.eclair.wire.protocol.Error
import fr.acinq.eclair.{AcceptOpenChannel, InterceptOpenChannelCommand, InterceptOpenChannelReceived, RejectOpenChannel}

/**
 * Intercept OpenChannel and OpenDualFundedChannel messages received by the node. Respond to the peer
 * that received the request with AcceptOpenChannel to continue the open channel process,
 * optionally with modified local parameters, or fail the request by responding to the initiator
 * with RejectOpenChannel and an Error message.
 *
 * This example plugin rejects requests to open a channel from nodes with less than a minimum amount of total capacity
 * or too few public channels.
 */
object OpenChannelInterceptor {
  // @formatter:off
  private case class WrappedGetNodeResponse(interceptOpenChannelReceived: InterceptOpenChannelReceived, response: Router.GetNodeResponse) extends InterceptOpenChannelCommand
  // @formatter:on

  //
  def apply(minActiveChannels: Int, minTotalCapacity: Satoshi, router: ActorRef[Any]): Behavior[InterceptOpenChannelCommand] = {
    Behaviors.setup {
      context => {
        new OpenChannelInterceptor(minActiveChannels, minTotalCapacity, router, context).start()
      }
    }
  }

}

class OpenChannelInterceptor(minActiveChannels: Int, minTotalCapacity: Satoshi, router: ActorRef[Any], context: ActorContext[InterceptOpenChannelCommand]) {
  import OpenChannelInterceptor._

  private def start(): Behavior[InterceptOpenChannelCommand] = {
    Behaviors.receiveMessage[InterceptOpenChannelCommand] {
      case o: InterceptOpenChannelReceived =>
        val adapter = context.messageAdapter[Router.GetNodeResponse](nodeResponse => WrappedGetNodeResponse(o, nodeResponse))
        router ! GetNode(adapter, o.localParams.nodeId)
        Behaviors.same
      case WrappedGetNodeResponse(o, PublicNode(_, activeChannels, _)) if activeChannels < minActiveChannels =>
        o.replyTo ! RejectOpenChannel(o.temporaryChannelId, Error(o.temporaryChannelId, s"rejected, less than $minActiveChannels active channels"))
        Behaviors.same
      case WrappedGetNodeResponse(o, PublicNode(_, _, totalCapacity)) if totalCapacity < minTotalCapacity =>
        o.replyTo ! RejectOpenChannel(o.temporaryChannelId, Error(o.temporaryChannelId, s"rejected, less than $minTotalCapacity total capacity"))
        Behaviors.same
      case WrappedGetNodeResponse(o, UnknownNode(_)) =>
        o.replyTo ! RejectOpenChannel(o.temporaryChannelId, Error(o.temporaryChannelId, s"rejected, no public channels"))
        Behaviors.same
      case WrappedGetNodeResponse(o, PublicNode(_, _, _)) =>
        o.replyTo ! AcceptOpenChannel(o.temporaryChannelId, o.localParams)
        Behaviors.same
    }
  }

}
