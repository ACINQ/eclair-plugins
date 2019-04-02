package fr.acinq.eclair.plugins.gossip

import java.io.File
import java.sql.DriverManager

import akka.actor.{Actor, ActorLogging, Props}
import fr.acinq.eclair._
import fr.acinq.eclair.router.{NORMAL => _, _}
import grizzled.slf4j.Logging

class GossipPlugin extends Plugin with Logging {

  override def onSetup(setup: Setup): Unit = {}

  override def onKit(kit: Kit): Unit = {
    logger.info("listening to network events...")
    val sqlite = DriverManager.getConnection(s"jdbc:sqlite:${new File(Boot.datadir, "appendnetwork.sqlite")}")
    val db = new AppendOnlyNetworkDb(sqlite)
    kit.system.actorOf(Props(new NetworkListener(db)))
  }
}


class NetworkListener(db: AppendOnlyNetworkDb) extends Actor with ActorLogging {

  context.system.eventStream.subscribe(self, classOf[NetworkEvent])

  override def receive: Receive = {

    case NodesDiscovered(nodes) =>
      nodes.foreach(db.addNode)

    case ChannelsDiscovered(channels) =>
      channels.foreach { case SingleChannelDiscovered(ann, capacity) => db.addChannel(ann, capacity) }

    case ChannelUpdatesReceived(updates) =>
      updates.foreach(db.addUpdate)
  }
}