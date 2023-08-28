package fr.acinq.eclair.plugins.tabconf

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter.ClassicSchedulerOps
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.server.Route
import fr.acinq.bitcoin.scalacompat.ByteVector32
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.api.serde.FormParamExtractors._
import fr.acinq.eclair.payment.{Bolt12Invoice, MinimalBolt12Invoice}
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import fr.acinq.eclair.{MilliSatoshi, TimestampMilli}

import scala.concurrent.duration.DurationInt

object ApiHandlers {

  import ApiSerializers.formats
  import fr.acinq.eclair.api.serde.JsonSupport.{marshaller, serialization}

  private case class ReceivedOfferPayment(amount: Option[MilliSatoshi], paymentHash: ByteVector32, description: String)

  def registerRoutes(kit: TabconfTipsKit, eclairDirectives: EclairDirectives): Route = {
    import eclairDirectives._

    implicit val scheduler: Scheduler = kit.actorSystem.scheduler.toTyped

    val createOffer: Route = postRequest("createoffer") { implicit t =>
      formFields("description".as[String], "amountMsat".as[MilliSatoshi].?) {
        (description, amount_opt) =>
          val offer = kit.tipsHandler.ask((ref: ActorRef[Offer]) => TipsHandler.CreateOffer(ref, description, amount_opt))
          complete(offer)
      }
    }

    val listOffers: Route = postRequest("listoffers") { implicit t =>
      val offers = kit.tipsDb.listOffers()
      complete(offers)
    }

    val listReceived: Route = postRequest("listreceivedofferpayments") { implicit t =>
      val received = kit.nodeParams.db.payments.listReceivedIncomingPayments(TimestampMilli.now() - 24.hours, TimestampMilli.now(), None)
      val result = received.flatMap(payment => payment.invoice match {
        case invoice: Bolt12Invoice => Some(ReceivedOfferPayment(Some(invoice.amount), invoice.paymentHash, invoice.description.swap.getOrElse("none")))
        case invoice: MinimalBolt12Invoice => Some(ReceivedOfferPayment(invoice.amount_opt, invoice.paymentHash, invoice.description.swap.getOrElse("none")))
        case _ => None
      })
      complete(result)
    }

    createOffer ~ listOffers ~ listReceived
  }

}
