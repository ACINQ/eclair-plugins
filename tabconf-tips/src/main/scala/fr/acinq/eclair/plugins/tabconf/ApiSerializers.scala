package fr.acinq.eclair.plugins.tabconf

import fr.acinq.bitcoin.scalacompat.ByteVector32
import fr.acinq.eclair.json.ConvertClassSerializer
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import fr.acinq.eclair.wire.protocol.{OfferTypes, TlvStream}
import org.json4s.Formats

object ApiSerializers {

  private case class OfferJson(offerId: ByteVector32, offer: String, records: TlvStream[OfferTypes.OfferTlv])

  private object OfferSerializer extends ConvertClassSerializer[Offer](offer => OfferJson(offer.offerId, offer.encode(), offer.records))

  implicit val formats: Formats = fr.acinq.eclair.api.serde.JsonSupport.formats + OfferSerializer

}
