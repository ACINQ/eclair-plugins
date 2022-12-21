/*
 * Copyright 2022 ACINQ SAS
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

package fr.acinq.eclair.plugins.gossip

import fr.acinq.bitcoin.scalacompat.Satoshi
import fr.acinq.eclair.db.sqlite.SqliteUtils
import fr.acinq.eclair.wire.protocol.LightningMessageCodecs.{channelAnnouncementCodec, channelUpdateCodec, nodeAnnouncementCodec}
import fr.acinq.eclair.wire.protocol.{ChannelAnnouncement, ChannelUpdate, NodeAnnouncement}

import java.sql.Connection

class AppendOnlyNetworkDb(sqlite: Connection) {

  import SqliteUtils._

  using(sqlite.createStatement()) { statement =>
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS nodes (node_id TEXT NOT NULL, data BLOB NOT NULL, timestamp INTEGER NOT NULL)")
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS channels (short_channel_id TEXT NOT NULL, node_id_1 TEXT NOT NULL, node_id_2 TEXT NOT NULL, capacity_sat INTEGER NOT NULL, channel_announcement BLOB NOT NULL)")
    statement.executeUpdate("CREATE TABLE IF NOT EXISTS updates (short_channel_id TEXT NOT NULL, dont_forward INTEGER NOT NULL, is_node_1 INTEGER NOT NULL, is_enabled INTEGER NOT NULL, cltv_expiry_delta INTEGER NOT NULL, fee_base_msat INTEGER NOT NULL, fee_proportional_millionths INTEGER NOT NULL, htlc_minimum_msat INTEGER NOT NULL, htlc_maximum_msat INTEGER NOT NULL, timestamp INTEGER NOT NULL, channel_update BLOB NOT NULL)")
  }

  def addNode(n: NodeAnnouncement): Unit = {
    using(sqlite.prepareStatement("INSERT OR IGNORE INTO nodes VALUES (?, ?, ?)")) { statement =>
      statement.setString(1, n.nodeId.toHex)
      statement.setBytes(2, nodeAnnouncementCodec.encode(n).require.toByteArray)
      statement.setLong(3, n.timestamp.toLong)
      statement.executeUpdate()
    }
  }

  def addChannel(c: ChannelAnnouncement, capacity: Satoshi): Unit = {
    using(sqlite.prepareStatement("INSERT OR IGNORE INTO channels VALUES (?, ?, ?, ?, ?)")) { statement =>
      statement.setString(1, c.shortChannelId.toString)
      statement.setString(2, c.nodeId1.toHex)
      statement.setString(3, c.nodeId2.toHex)
      statement.setLong(4, capacity.toLong)
      statement.setBytes(5, channelAnnouncementCodec.encode(c).require.toByteArray)
      statement.executeUpdate()
    }
  }

  def addUpdate(u: ChannelUpdate): Unit = {
    using(sqlite.prepareStatement("INSERT OR IGNORE INTO updates VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) { statement =>
      statement.setString(1, u.shortChannelId.toString)
      statement.setBoolean(2, u.messageFlags.dontForward)
      statement.setBoolean(3, u.channelFlags.isNode1)
      statement.setBoolean(4, u.channelFlags.isEnabled)
      statement.setInt(5, u.cltvExpiryDelta.toInt)
      statement.setLong(6, u.feeBaseMsat.toLong)
      statement.setLong(7, u.feeProportionalMillionths)
      statement.setLong(8, u.htlcMinimumMsat.toLong)
      statement.setLong(9, u.htlcMaximumMsat.toLong)
      statement.setLong(10, u.timestamp.toLong)
      statement.setBytes(11, channelUpdateCodec.encode(u).require.toByteArray)
      statement.executeUpdate()
    }
  }


}
