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
import fr.acinq.eclair.NodeParams
import fr.acinq.eclair.payment.offer.OfferManager.{HandleInvoiceRequest, HandlePayment, HandlerCommand, InvoiceRequestActor, PaymentActor}
import fr.acinq.eclair.payment.relay.Relayer.RelayFees
import fr.acinq.eclair.router.Router
import fr.acinq.eclair.router.Router.{BlindedRouteRequest, PaymentRouteResponse}
import fr.acinq.eclair.wire.protocol.OfferTypes.InvoiceRequest

object CustomOfferHandler {
  def apply(nodeParams: NodeParams, config: CustomOfferConfig, router: akka.actor.ActorRef): Behavior[HandlerCommand] = {
    Behaviors.setup(context =>
      Behaviors.receiveMessage {
        case HandleInvoiceRequest(replyTo, invoiceRequest) =>
          val routeParams = nodeParams.routerConf.pathFindingExperimentConf.getRandomConf().getDefaultRouteParams
          val source = invoiceRequest.payerId // We require the payer to use its public node id and we do all the routing ourselves (probably not a good idea, it's just for example purposes).
          router ! BlindedRouteRequest(context.spawnAnonymous(waitForRoute(replyTo, config, invoiceRequest)), source, nodeParams.nodeId, invoiceRequest.amount, routeParams, 1, Router.Ignore(config.avoidNodes, Set.empty))
          Behaviors.same
        case HandlePayment(replyTo, offer, invoiceData) =>
          replyTo ! PaymentActor.AcceptPayment()
          Behaviors.same
      }
    )
  }

  private def waitForRoute(replyTo: ActorRef[InvoiceRequestActor.Command], config: CustomOfferConfig, invoiceRequest: InvoiceRequest): Behavior[PaymentRouteResponse] = {
    Behaviors.receiveMessage {
      case Router.RouteResponse(routes) =>
        replyTo ! InvoiceRequestActor.ApproveRequest(invoiceRequest.amount, routes.map(r => InvoiceRequestActor.Route(r.hops, config.maxFinalExpiryDelta, feeOverride_opt = Some(RelayFees.zero))))
        Behaviors.stopped
      case Router.PaymentRouteNotFound(error) =>
        replyTo ! InvoiceRequestActor.RejectRequest("unreachable node")
        Behaviors.stopped
    }
  }
}
