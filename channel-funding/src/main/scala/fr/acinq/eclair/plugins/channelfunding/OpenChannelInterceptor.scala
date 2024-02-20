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

package fr.acinq.eclair.plugins.channelfunding

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import fr.acinq.eclair.router.Router
import fr.acinq.eclair.router.Router.{GetNode, PublicNode, UnknownNode}
import fr.acinq.eclair.wire.protocol.Error
import fr.acinq.eclair.{AcceptOpenChannel, InterceptOpenChannelCommand, InterceptOpenChannelReceived, RejectOpenChannel}

/**
 * Intercept OpenChannel and OpenDualFundedChannel messages received by the node. Respond to the peer that received the
 * request with AcceptOpenChannel to continue the open channel process, optionally with modified default parameters, or
 * fail the request by responding to the initiator with RejectOpenChannel and an Error message.
 *
 * This example plugin decides how much funds (if any) the non-initiator should put into a dual-funded channel. It also
 * demonstrates how to reject requests from nodes with less than a minimum amount of total capacity or too few public
 * channels.
 */
object OpenChannelInterceptor {
  private case class WrappedGetNodeResponse(interceptOpenChannelReceived: InterceptOpenChannelReceived, response: Router.GetNodeResponse) extends InterceptOpenChannelCommand

  def apply(config: ChannelFundingPluginConfig, router: ActorRef[Any]): Behavior[InterceptOpenChannelCommand] = {
    Behaviors.setup {
      context => new OpenChannelInterceptor(config, router, context).start()
    }
  }

}

class OpenChannelInterceptor(config: ChannelFundingPluginConfig, router: ActorRef[Any], context: ActorContext[InterceptOpenChannelCommand]) {

  import OpenChannelInterceptor._

  private def start(): Behavior[InterceptOpenChannelCommand] = {
    Behaviors.receiveMessage {
      case o: InterceptOpenChannelReceived =>
        if (config.remoteNodeRequirements.isWhitelisted(o.openChannelNonInitiator.remoteNodeId) || config.fundingPolicy.whitelist.contains(o.openChannelNonInitiator.remoteNodeId)) {
          acceptOpenChannel(o)
        } else {
          // We check that the remote node meets our requirements.
          val adapter = context.messageAdapter[Router.GetNodeResponse](nodeResponse => WrappedGetNodeResponse(o, nodeResponse))
          router ! GetNode(adapter, o.openChannelNonInitiator.remoteNodeId)
        }
        Behaviors.same
      case WrappedGetNodeResponse(o, PublicNode(_, activeChannels, _)) if activeChannels < config.remoteNodeRequirements.minActiveChannels =>
        rejectOpenChannel(o, s"channel request rejected, remote node has less than ${config.remoteNodeRequirements.minActiveChannels} active channels")
        Behaviors.same
      case WrappedGetNodeResponse(o, PublicNode(_, _, totalCapacity)) if totalCapacity < config.remoteNodeRequirements.minTotalCapacity =>
        rejectOpenChannel(o, s"channel request rejected, remote node has less than ${config.remoteNodeRequirements.minTotalCapacity} of public total capacity")
        Behaviors.same
      case WrappedGetNodeResponse(o, UnknownNode(_)) =>
        if (!config.remoteNodeRequirements.allowPrivateNodes) {
          rejectOpenChannel(o, "channel request rejected, remote node has no public channels")
        } else {
          acceptOpenChannel(o)
        }
        Behaviors.same
      case WrappedGetNodeResponse(o, PublicNode(_, _, _)) =>
        acceptOpenChannel(o)
        Behaviors.same
    }
  }

  private def acceptOpenChannel(o: InterceptOpenChannelReceived): Unit = {
    o.replyTo ! AcceptOpenChannel(o.temporaryChannelId, o.defaultParams, config.fundingPolicy.fundingAmountFor(o.openChannelNonInitiator.remoteNodeId))
  }

  private def rejectOpenChannel(o: InterceptOpenChannelReceived, error: String): Unit = {
    o.replyTo ! RejectOpenChannel(o.temporaryChannelId, Error(o.temporaryChannelId, error))
  }
}
