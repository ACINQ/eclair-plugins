package fr.acinq.eclair.plugins.tabconf

import fr.acinq.bitcoin.scalacompat.Block
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import fr.acinq.eclair.{Features, MilliSatoshiLong, randomBytes32, randomKey}
import org.scalatest.funsuite.AnyFunSuiteLike

import java.sql.DriverManager

class TipsDbSpec extends AnyFunSuiteLike {

  test("create/get/list offers") {
    val db = new SqliteTipsDb(DriverManager.getConnection("jdbc:sqlite::memory:"))
    assert(db.listOffers().isEmpty)

    val offer1 = Offer(Some(5_000 msat), "offers are real", randomKey().publicKey, Features.empty, Block.RegtestGenesisBlock.hash)
    db.addOffer(offer1)
    val offer2 = Offer(None, "offers are a lie", randomKey().publicKey, Features.empty, Block.RegtestGenesisBlock.hash)
    db.addOffer(offer2)

    assert(db.getOffer(randomBytes32()).isEmpty)
    assert(db.getOffer(offer1.offerId).contains(offer1))
    assert(db.getOffer(offer2.offerId).contains(offer2))
    assert(db.listOffers().toSet == Set(offer1, offer2))
  }

}
