/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.eclair.plugins.customoffer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import fr.acinq.bitcoin.scalacompat.Crypto.{PrivateKey, PublicKey}
import fr.acinq.eclair.message.OnionMessages
import fr.acinq.eclair.message.OnionMessages.{IntermediateNode, Recipient}
import fr.acinq.eclair.payment.offer.OfferManager
import fr.acinq.eclair.payment.offer.OfferManager.RegisterOffer
import fr.acinq.eclair.router.Router.{MessageRoute, MessageRouteNotFound, MessageRouteRequest, MessageRouteResponse}
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import fr.acinq.eclair.{Features, MilliSatoshi, NodeParams, randomBytes32}

object CustomOfferCreator {
  sealed trait Command

  case class CreateOffer(replyTo: ActorRef[CreateOfferResult], amount: MilliSatoshi, description: String, introductionNode: PublicKey) extends Command

  sealed trait CreateOfferResult

  case class CreatedOffer(offer: Offer) extends CreateOfferResult

  case class CreateOfferError(reason: String) extends CreateOfferResult

  def apply(nodeParams: NodeParams, config: CustomOfferConfig, router: akka.actor.ActorRef, offerManager: ActorRef[RegisterOffer]): Behavior[Command] = {
    Behaviors.setup(context =>
      Behaviors.receiveMessage {
        case CreateOffer(replyTo, amount, description, introductionNode) =>
          val handler = context.spawnAnonymous(CustomOfferHandler(nodeParams, config, router))
          router ! MessageRouteRequest(context.spawnAnonymous(waitForRoute(replyTo, nodeParams, handler, offerManager, amount, description, introductionNode)), introductionNode, nodeParams.nodeId, config.avoidNodes)
          Behaviors.same
      }
    )
  }

  private def waitForRoute(replyTo: ActorRef[CreateOfferResult], nodeParams: NodeParams, handler: ActorRef[OfferManager.HandlerCommand], offerManager: ActorRef[OfferManager.RegisterOffer], amount: MilliSatoshi, description: String, introductionNode: PublicKey): Behavior[MessageRouteResponse] = {
    Behaviors.receiveMessage {
      case MessageRoute(intermediateNodes, _) =>
        val pathId = randomBytes32()
        val path = OnionMessages.buildRoute(
          PrivateKey(pathId),
          (introductionNode +: intermediateNodes :+ nodeParams.nodeId).map(IntermediateNode(_)), // We can add our node id as many times as we want to hide the true length of the path.
          Recipient(nodeParams.nodeId, Some(pathId)))
        val offer = Offer.withPaths(Some(amount), Some(description), Seq(path.route), Features.empty, nodeParams.chainHash)
        offerManager ! RegisterOffer(offer, None, Some(pathId), handler)
        replyTo ! (CreatedOffer(offer))
        Behaviors.stopped
      case MessageRouteNotFound(_) =>
        replyTo ! CreateOfferError("blinded route not found")
        Behaviors.stopped
    }
  }
}
