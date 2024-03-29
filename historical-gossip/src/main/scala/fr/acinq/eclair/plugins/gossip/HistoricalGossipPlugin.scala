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

import akka.actor.{Actor, ActorLogging, Props}
import fr.acinq.eclair._
import fr.acinq.eclair.db.sqlite.SqliteUtils
import fr.acinq.eclair.router.{ChannelUpdatesReceived, ChannelsDiscovered, NetworkEvent, NodesDiscovered}
import grizzled.slf4j.Logging

import java.io.File

class HistoricalGossipPlugin extends Plugin with Logging {

  var db: AppendOnlyNetworkDb = _

  override def params: PluginParams = new PluginParams {
    override def name: String = "HistoricalGossipPlugin"
  }

  override def onSetup(setup: Setup): Unit = {
    // We create our DB in the per-chain data directory.
    val chain = setup.config.getString("chain")
    val chainDir = new File(setup.datadir, chain)
    db = new AppendOnlyNetworkDb(SqliteUtils.openSqliteFile(chainDir, "historical-gossip.sqlite", exclusiveLock = false, journalMode = "wal", syncFlag = "normal"))
  }

  override def onKit(kit: Kit): Unit = {
    kit.system.actorOf(Props(new NetworkListener(db)))
  }

}

class NetworkListener(db: AppendOnlyNetworkDb) extends Actor with ActorLogging {

  context.system.eventStream.subscribe(self, classOf[NetworkEvent])

  override def receive: Receive = {
    case NodesDiscovered(nodes) => nodes.foreach(db.addNode)
    case ChannelsDiscovered(channels) => channels.foreach(channel => db.addChannel(channel.ann, channel.capacity))
    case ChannelUpdatesReceived(updates) => updates.foreach(db.addUpdate)
  }
}