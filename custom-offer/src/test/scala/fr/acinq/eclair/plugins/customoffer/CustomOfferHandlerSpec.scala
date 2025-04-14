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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.adapter._
import fr.acinq.eclair.payment.offer.OfferManager.InvoiceRequestActor.ApproveRequest
import fr.acinq.eclair.payment.offer.OfferManager.PaymentActor.AcceptPayment
import fr.acinq.eclair.payment.offer.OfferManager.{HandleInvoiceRequest, HandlePayment, InvoiceRequestActor, PaymentActor}
import fr.acinq.eclair.payment.offer.OfferPaymentMetadata.MinimalInvoiceData
import fr.acinq.eclair.payment.relay.Relayer.RelayFees
import fr.acinq.eclair.router.Router
import fr.acinq.eclair.router.Router.ChannelHop
import fr.acinq.eclair.wire.protocol.OfferTypes._
import fr.acinq.eclair.{CltvExpiryDelta, Features, MilliSatoshiLong, TestConstants, TimestampSecond, randomBytes32, randomKey}
import org.scalatest.funsuite.AnyFunSuiteLike

class CustomOfferHandlerSpec extends ScalaTestWithActorTestKit with AnyFunSuiteLike {

  test("handle invoice request") {
    val nodeParams = TestConstants.Alice.nodeParams
    val config = CustomOfferConfig(Set(randomKey().publicKey), CltvExpiryDelta(123))
    val router = TestProbe[Router.BlindedRouteRequest]()
    val customOfferHandler = testKit.spawn(CustomOfferHandler(nodeParams, config, router.ref.toClassic))

    val invoiceRequestActor = TestProbe[InvoiceRequestActor.Command]()
    val offer = Offer(Some(1000 msat), Some("my custom offer"), nodeParams.nodeId, Features.empty, nodeParams.chainHash)
    val payerKey = randomKey()
    val invoiceRequest = InvoiceRequest(offer, 20000 msat, 1, Features.empty, payerKey, nodeParams.chainHash)
    customOfferHandler ! HandleInvoiceRequest(invoiceRequestActor.ref, invoiceRequest)

    val routeRequest = router.expectMessageType[Router.BlindedRouteRequest]
    assert(routeRequest.source == payerKey.publicKey)
    val dummyHop = ChannelHop.dummy(randomKey().publicKey, 0 msat, 0, CltvExpiryDelta(0))
    routeRequest.replyTo ! Router.RouteResponse(Seq(Router.Route(20000 msat, Seq(dummyHop), None)))

    val approve = invoiceRequestActor.expectMessageType[ApproveRequest]
    assert(approve.amount == 20000.msat)
    assert(approve.routes.map(_.hops) == Seq(Seq(dummyHop)))
  }

  test("handle payment") {
    val nodeParams = TestConstants.Alice.nodeParams
    val config = CustomOfferConfig(Set(randomKey().publicKey), CltvExpiryDelta(123))
    val router = TestProbe[Router.BlindedRouteRequest]()
    val customOfferHandler = testKit.spawn(CustomOfferHandler(nodeParams, config, router.ref.toClassic))

    val paymentActor = TestProbe[PaymentActor.Command]()
    val offer = Offer(Some(1000 msat), Some("my custom offer"), nodeParams.nodeId, Features.empty, nodeParams.chainHash)
    customOfferHandler ! HandlePayment(paymentActor.ref, offer, MinimalInvoiceData(randomBytes32(), randomKey().publicKey, TimestampSecond.now(), 1, 100_000_000 msat, RelayFees.zero, None))
    paymentActor.expectMessage(AcceptPayment())
  }
}
