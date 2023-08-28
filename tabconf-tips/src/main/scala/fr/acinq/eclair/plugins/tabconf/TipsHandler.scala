package fr.acinq.eclair.plugins.tabconf

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import fr.acinq.bitcoin.scalacompat.ByteVector32
import fr.acinq.eclair.payment.offer.OfferManager
import fr.acinq.eclair.payment.receive.MultiPartHandler.ReceivingRoute
import fr.acinq.eclair.wire.protocol.GenericTlv
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import fr.acinq.eclair.{CltvExpiryDelta, Features, MilliSatoshi, MilliSatoshiLong, NodeParams, UInt64}
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets

object TipsHandler {

  // @formatter:off
  sealed trait Command
  case class CreateOffer(replyTo: ActorRef[Offer], description: String, amount_opt: Option[MilliSatoshi]) extends Command
  private case class WrappedOfferManagerCommand(command: OfferManager.HandlerCommand) extends Command
  // @formatter:on

  def apply(nodeParams: NodeParams, db: TipsDb, offerManager: ActorRef[OfferManager.Command]): Behavior[Command] = {
    Behaviors.setup { context =>
      val offers = db.listOffers()
      val adapter = context.messageAdapter[OfferManager.HandlerCommand](c => WrappedOfferManagerCommand(c))
      offers.foreach(offer => offerManager ! OfferManager.RegisterOffer(offer, nodeParams.privateKey, None, adapter))
      val actor = new TipsHandler(nodeParams, db, offerManager, context)
      actor.run(offers.map(offer => offer.offerId -> offer).toMap)
    }
  }

}

case class TipsHandler(nodeParams: NodeParams, db: TipsDb, offerManager: ActorRef[OfferManager.Command], context: ActorContext[TipsHandler.Command]) {

  import TipsHandler._

  private val log = context.log

  def run(offers: Map[ByteVector32, Offer]): Behavior[Command] = {
    Behaviors.receiveMessage {
      case CreateOffer(replyTo, description, amount_opt) =>
        val adapter = context.messageAdapter[OfferManager.HandlerCommand](c => WrappedOfferManagerCommand(c))
        val offer = Offer(amount_opt, description, nodeParams.nodeId, Features.empty, nodeParams.chainHash)
        db.addOffer(offer)
        offerManager ! OfferManager.RegisterOffer(offer, nodeParams.privateKey, None, adapter)
        replyTo ! offer
        log.info("registered new offer with offer_id={}", offer.offerId)
        run(offers + (offer.offerId -> offer))
      case WrappedOfferManagerCommand(command) =>
        command match {
          case OfferManager.HandleInvoiceRequest(replyTo, invoiceRequest) =>
            offers.get(invoiceRequest.offer.offerId) match {
              case Some(offer) =>
                val amount = invoiceRequest.amount.orElse(offer.amount).getOrElse(5_000_000 msat)
                log.info("received invoice_request for offer_id={} and amount={}", offer.offerId, amount)
                replyTo ! OfferManager.InvoiceRequestActor.ApproveRequest(amount, Seq(ReceivingRoute(Seq(nodeParams.nodeId), CltvExpiryDelta(720))))
              case None =>
                log.info("received invoice_request for unknown offer={}", invoiceRequest.offer.encode())
                replyTo ! OfferManager.InvoiceRequestActor.RejectRequest("not my offer!")
            }
            Behaviors.same
          case OfferManager.HandlePayment(replyTo, offerId, _) =>
            log.info("received payment for offer_id={}", offerId)
            replyTo ! OfferManager.PaymentActor.AcceptPayment(customTlvs = Set(GenericTlv(UInt64(1105), ByteVector.view("thanks <3".getBytes(StandardCharsets.US_ASCII)))))
            Behaviors.same
        }
    }
  }

}