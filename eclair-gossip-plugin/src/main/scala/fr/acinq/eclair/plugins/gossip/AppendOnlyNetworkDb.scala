package fr.acinq.eclair.plugins.gossip

import java.sql.Connection

import fr.acinq.bitcoin.{ByteVector32, Satoshi}
import fr.acinq.eclair.db.sqlite.SqliteUtils
import fr.acinq.eclair.router.Announcements
import fr.acinq.eclair.wire.{ChannelAnnouncement, ChannelUpdate, NodeAnnouncement}

class AppendOnlyNetworkDb(sqlite: Connection) {

  import SqliteUtils._


  using(sqlite.createStatement()) { statement =>
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS nodes (node_id BLOB NOT NULL, timestamp INTEGER NOT NULL)")
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS channels (short_channel_id INTEGER NOT NULL, node_id_1 BLOB NOT NULL, node_id_2 BLOB NOT NULL, capacity_sat INTEGER NOT NULL)")
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS updates (short_channel_id INTEGER NOT NULL, node_flag INTEGER NOT NULL, timestamp INTEGER NOT NULL, flags BLOB NOT NULL, cltv_expiry_delta INTEGER NOT NULL, htlc_minimum_msat INTEGER NOT NULL, fee_base_msat INTEGER NOT NULL, fee_proportional_millionths INTEGER NOT NULL, htlc_maximum_msat INTEGER)")
  }

  def addNode(n: NodeAnnouncement): Unit = {
    using(sqlite.prepareStatement("INSERT OR IGNORE INTO nodes VALUES (?, ?)")) { statement =>
      statement.setBytes(1, n.nodeId.toBin.toArray)
      statement.setLong(2, n.timestamp)
      statement.executeUpdate()
    }
  }

  def addChannel(c: ChannelAnnouncement, capacity: Satoshi): Unit = {
    using(sqlite.prepareStatement("INSERT OR IGNORE INTO channels VALUES (?, ?, ?, ?)")) { statement =>
      statement.setLong(1, c.shortChannelId.toLong)
      statement.setBytes(2, c.nodeId1.toArray)
      statement.setBytes(3, c.nodeId2.toArray)
      statement.setLong(4, capacity.amount)
      statement.executeUpdate()
    }
  }

  def addUpdate(u: ChannelUpdate): Unit = {
    using(sqlite.prepareStatement("INSERT OR IGNORE INTO channel_updates VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) { statement =>
      statement.setLong(1, u.shortChannelId.toLong)
      statement.setBoolean(2, Announcements.isNode1(u.channelFlags))
      statement.setLong(3, u.timestamp)
      statement.setBytes(4, Array(u.messageFlags, u.channelFlags))
      statement.setInt(5, u.cltvExpiryDelta)
      statement.setLong(6, u.htlcMinimumMsat)
      statement.setLong(7, u.feeBaseMsat)
      statement.setLong(8, u.feeProportionalMillionths)
      u.htlcMaximumMsat match {
        case Some(value) => statement.setLong(9, value)
        case None => statement.setNull(9, java.sql.Types.INTEGER)
      }
      statement.executeUpdate()
    }
  }


}
