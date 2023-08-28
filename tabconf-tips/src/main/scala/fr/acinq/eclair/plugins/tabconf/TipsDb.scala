package fr.acinq.eclair.plugins.tabconf

import fr.acinq.bitcoin.scalacompat.ByteVector32
import fr.acinq.eclair.TimestampSecond
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer

import java.sql.Connection

trait TipsDb {

  def addOffer(offer: Offer): Unit

  def getOffer(offerId: ByteVector32): Option[Offer]

  def listOffers(): Seq[Offer]

}

object SqliteTipsDb {
  private val CURRENT_VERSION = 1
  private val DB_NAME = "tabconf_tips"
}

class SqliteTipsDb(sqlite: Connection) extends TipsDb {

  import SqliteTipsDb._
  import fr.acinq.eclair.db.jdbc.JdbcUtils.ExtendedResultSet._
  import fr.acinq.eclair.db.sqlite.SqliteUtils._

  using(sqlite.createStatement(), inTransaction = true) { statement =>
    getVersion(statement, DB_NAME) match {
      case None =>
        statement.executeUpdate("CREATE TABLE offers (offer_id TEXT NOT NULL PRIMARY KEY, offer TEXT NOT NULL, created_timestamp INTEGER NOT NULL)")
        statement.executeUpdate("CREATE INDEX created_timestamp_idx ON offers(created_timestamp)")
      case Some(CURRENT_VERSION) => () // table is up-to-date, nothing to do
      case Some(unknownVersion) => throw new RuntimeException(s"Unknown version of DB $DB_NAME found, version=$unknownVersion")
    }
    setVersion(statement, DB_NAME, CURRENT_VERSION)
  }

  override def addOffer(offer: Offer): Unit = {
    using(sqlite.prepareStatement("INSERT INTO offers (offer_id, offer, created_timestamp) VALUES (?, ?, ?)")) { statement =>
      statement.setString(1, offer.offerId.toHex)
      statement.setString(2, offer.toString())
      statement.setLong(3, TimestampSecond.now().toLong)
      statement.executeUpdate()
    }
  }

  override def getOffer(offerId: ByteVector32): Option[Offer] = {
    using(sqlite.prepareStatement("SELECT * FROM offers WHERE offer_id = ?")) { statement =>
      statement.setString(1, offerId.toHex)
      statement.executeQuery().flatMap(rs => {
        Offer.decode(rs.getString("offer")).toOption
      }).lastOption
    }
  }

  override def listOffers(): Seq[Offer] = {
    using(sqlite.prepareStatement("SELECT * FROM offers")) { statement =>
      statement.executeQuery().flatMap(rs => Offer.decode(rs.getString("offer")).toOption).toSeq
    }
  }
}