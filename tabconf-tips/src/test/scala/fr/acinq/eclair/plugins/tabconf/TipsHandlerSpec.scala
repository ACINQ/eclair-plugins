package fr.acinq.eclair.plugins.tabconf

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import fr.acinq.bitcoin.scalacompat.Block
import fr.acinq.eclair.payment.offer.OfferManager
import fr.acinq.eclair.wire.protocol.OfferTypes.{InvoiceRequest, Offer}
import fr.acinq.eclair.{Features, MilliSatoshiLong, TestConstants, randomKey}
import org.scalatest.funsuite.AnyFunSuiteLike

import java.sql.DriverManager
import scala.concurrent.duration.DurationInt

class TipsHandlerSpec extends ScalaTestWithActorTestKit with AnyFunSuiteLike {

  test("create offer and get paid") {
    val nodeParams = TestConstants.Alice.nodeParams
    val db = new SqliteTipsDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
    val offerManager = TestProbe[OfferManager.Command]()

    val handler = testKit.spawn(TipsHandler(nodeParams, db, offerManager.ref))
    val sender = TestProbe[Offer]()
    handler ! TipsHandler.CreateOffer(sender.ref, "much bolt12 very reckless", Some(25_000_000 msat))
    val offer = sender.expectMessageType[Offer]
    assert(offer.amount.contains(25_000_000 msat))
    assert(offer.description == "much bolt12 very reckless")

    // On creation, the offer is registered within eclair's offer manager, which will forward all matching invoice requests.
    val adapter = offerManager.expectMessageType[OfferManager.RegisterOffer].handler
    val invoiceRequest = InvoiceRequest(offer, 30_000_000 msat, 1, Features.empty, randomKey(), Block.RegtestGenesisBlock.hash)
    val invoiceActor = TestProbe[OfferManager.InvoiceRequestActor.Command]()
    adapter ! OfferManager.HandleInvoiceRequest(invoiceActor.ref, invoiceRequest)
    assert(invoiceActor.expectMessageType[OfferManager.InvoiceRequestActor.ApproveRequest].amount == 30_000_000.msat)

    // Once the invoice request has been accepted, eclair's offer manager will request a final validation when a matching payment is received.
    val paymentActor = TestProbe[OfferManager.PaymentActor.Command]()
    adapter ! OfferManager.HandlePayment(paymentActor.ref, offer.offerId)
    paymentActor.expectMessageType[OfferManager.PaymentActor.AcceptPayment]

    testKit.stop(handler)
  }

  test("load offers at start-up") {
    val nodeParams = TestConstants.Alice.nodeParams
    val db = new SqliteTipsDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
    val offerManager = TestProbe[OfferManager.Command]()
    val previousOffers = Seq(
      Offer(Some(10_000_000 msat), "wine", nodeParams.nodeId, Features.empty, Block.RegtestGenesisBlock.hash),
      Offer(Some(15_000_000 msat), "champagne", nodeParams.nodeId, Features.empty, Block.RegtestGenesisBlock.hash),
      Offer(None, "tips", nodeParams.nodeId, Features.empty, Block.RegtestGenesisBlock.hash)
    )
    previousOffers.foreach(offer => db.addOffer(offer))

    val handler = testKit.spawn(TipsHandler(nodeParams, db, offerManager.ref))
    val offer1 = offerManager.expectMessageType[OfferManager.RegisterOffer]
    val offer2 = offerManager.expectMessageType[OfferManager.RegisterOffer]
    val offer3 = offerManager.expectMessageType[OfferManager.RegisterOffer]
    offerManager.expectNoMessage(100 millis)
    assert(Set(offer1, offer2, offer3).map(_.offer.offerId) == previousOffers.map(_.offerId).toSet)

    val invoiceRequest = InvoiceRequest(offer1.offer, 10_000_000 msat, 1, Features.empty, randomKey(), Block.RegtestGenesisBlock.hash)
    val invoiceActor = TestProbe[OfferManager.InvoiceRequestActor.Command]()
    offer1.handler ! OfferManager.HandleInvoiceRequest(invoiceActor.ref, invoiceRequest)
    invoiceActor.expectMessageType[OfferManager.InvoiceRequestActor.ApproveRequest]

    val paymentActor = TestProbe[OfferManager.PaymentActor.Command]()
    offer1.handler ! OfferManager.HandlePayment(paymentActor.ref, offer1.offer.offerId)
    paymentActor.expectMessageType[OfferManager.PaymentActor.AcceptPayment]

    testKit.stop(handler)
  }

  test("reject payments for unknown offers") {
    val nodeParams = TestConstants.Alice.nodeParams
    val db = new SqliteTipsDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
    val offerManager = TestProbe[OfferManager.Command]()

    val handler = testKit.spawn(TipsHandler(nodeParams, db, offerManager.ref))
    val sender = TestProbe[Offer]()
    handler ! TipsHandler.CreateOffer(sender.ref, "nobody wants that offer", Some(250_000_000 msat))
    sender.expectMessageType[Offer]

    val adapter = offerManager.expectMessageType[OfferManager.RegisterOffer].handler
    val unknownOffer = Offer(None, "eV1l 0Ff3r", randomKey().publicKey, Features.empty, Block.RegtestGenesisBlock.hash)
    val invoiceRequest = InvoiceRequest(unknownOffer, 1_000 msat, 1, Features.empty, randomKey(), Block.RegtestGenesisBlock.hash)
    val invoiceActor = TestProbe[OfferManager.InvoiceRequestActor.Command]()
    adapter ! OfferManager.HandleInvoiceRequest(invoiceActor.ref, invoiceRequest)
    invoiceActor.expectMessageType[OfferManager.InvoiceRequestActor.RejectRequest]

    testKit.stop(handler)
  }

}
