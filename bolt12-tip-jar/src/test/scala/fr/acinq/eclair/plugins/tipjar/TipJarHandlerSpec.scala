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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import fr.acinq.bitcoin.scalacompat.ByteVector64
import fr.acinq.eclair.payment.offer.OfferManager.InvoiceRequestActor.{ApproveRequest, Route}
import fr.acinq.eclair.payment.offer.OfferManager.PaymentActor.AcceptPayment
import fr.acinq.eclair.payment.offer.OfferManager.{HandleInvoiceRequest, HandlePayment, InvoiceRequestActor, PaymentActor}
import fr.acinq.eclair.payment.offer.OfferPaymentMetadata.MinimalInvoiceData
import fr.acinq.eclair.payment.relay.Relayer.RelayFees
import fr.acinq.eclair.router.Router.ChannelHop
import fr.acinq.eclair.wire.protocol.OfferTypes._
import fr.acinq.eclair.wire.protocol.TlvStream
import fr.acinq.eclair.{CltvExpiryDelta, Features, MilliSatoshiLong, TestConstants, TimestampSecond, randomBytes32, randomKey}
import org.scalatest.funsuite.AnyFunSuiteLike

class TipJarHandlerSpec extends ScalaTestWithActorTestKit with AnyFunSuiteLike {

  ignore("handle invoice request") {
    val nodeParams = TestConstants.Alice.nodeParams
    val handler = testKit.spawn(TipJarHandler(Route(Seq(ChannelHop.dummy(nodeParams.nodeId, 0 msat, 0, CltvExpiryDelta(0))), CltvExpiryDelta(1000))))

    val probe = TestProbe[InvoiceRequestActor.Command]()
    val offer = Offer(None, Some("test tip jar"), nodeParams.nodeId, Features.empty, nodeParams.chainHash)
    val invoiceRequest = InvoiceRequest(TlvStream(offer.records.records ++ Set(InvoiceRequestMetadata(randomBytes32()), InvoiceRequestChain(nodeParams.chainHash), InvoiceRequestPayerId(randomKey().publicKey), Signature(ByteVector64.Zeroes))))
    handler ! HandleInvoiceRequest(probe.ref, invoiceRequest)

    val approve = probe.expectMessageType[ApproveRequest]
    assert(approve.amount == 100_000_000.msat)
  }

  ignore("handle payment") {
    val nodeParams = TestConstants.Alice.nodeParams
    val handler = testKit.spawn(TipJarHandler(Route(Seq(ChannelHop.dummy(nodeParams.nodeId, 0 msat, 0, CltvExpiryDelta(0))), CltvExpiryDelta(1000))))

    val probe = TestProbe[PaymentActor.Command]()
    val offer = Offer(None, Some("test tip jar"), nodeParams.nodeId, Features.empty, nodeParams.chainHash)
    handler ! HandlePayment(probe.ref, offer, MinimalInvoiceData(randomBytes32(), randomKey().publicKey, TimestampSecond.now(), 1, 100_000_000 msat, RelayFees.zero, None))
    probe.expectMessage(AcceptPayment())
  }

}
