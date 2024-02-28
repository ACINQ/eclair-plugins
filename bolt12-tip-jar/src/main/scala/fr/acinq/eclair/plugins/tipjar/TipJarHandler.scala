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

package fr.acinq.eclair.plugins.tipjar

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import fr.acinq.bitcoin.scalacompat.Crypto.PublicKey
import fr.acinq.eclair.payment.offer.OfferManager
import fr.acinq.eclair.payment.offer.OfferManager.HandlerCommand
import fr.acinq.eclair.payment.offer.OfferManager.InvoiceRequestActor.ApproveRequest
import fr.acinq.eclair.payment.offer.OfferManager.PaymentActor.AcceptPayment
import fr.acinq.eclair.payment.receive.MultiPartHandler.ReceivingRoute
import fr.acinq.eclair.{CltvExpiryDelta, MilliSatoshi}

object TipJarHandler {

  def apply(nodeId: PublicKey, defaultAmount: MilliSatoshi, maxFinalExpiryDelta: CltvExpiryDelta): Behavior[HandlerCommand] = {
    Behaviors.receiveMessage {
      case OfferManager.HandleInvoiceRequest(replyTo, invoiceRequest) =>
        replyTo ! ApproveRequest(invoiceRequest.amount.getOrElse(defaultAmount), Seq(ReceivingRoute(Seq(nodeId), maxFinalExpiryDelta)), None)
        Behaviors.same
      case OfferManager.HandlePayment(replyTo, _, _) =>
        replyTo ! AcceptPayment()
        Behaviors.same
    }
  }
}
