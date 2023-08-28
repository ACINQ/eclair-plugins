package fr.acinq.eclair.plugins.tabconf

import akka.actor.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, SupervisorStrategy}
import akka.http.scaladsl.server.Route
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.db.sqlite.SqliteUtils
import fr.acinq.eclair.{Kit, NodeParams, Plugin, PluginParams, RouteProvider, Setup}
import grizzled.slf4j.Logging

import java.io.File

class TabconfTipsPlugin extends Plugin with RouteProvider with Logging {

  private var db: TipsDb = _
  private var pluginKit: TabconfTipsKit = _

  override def params: PluginParams = new PluginParams {
    override def name: String = "TabconfTipsPlugin"
  }

  override def onSetup(setup: Setup): Unit = {
    // We create our DB in the per-chain data directory.
    val chain = setup.config.getString("chain")
    val chainDir = new File(setup.datadir, chain)
    db = new SqliteTipsDb(SqliteUtils.openSqliteFile(chainDir, "tabconf-tips.sqlite", exclusiveLock = false, journalMode = "wal", syncFlag = "normal"))
  }

  override def onKit(kit: Kit): Unit = {
    val tipsHandler = kit.system.spawn(Behaviors.supervise(TipsHandler(kit.nodeParams, db, kit.offerManager)).onFailure(SupervisorStrategy.restart), "tabconf-tips")
    pluginKit = TabconfTipsKit(kit.nodeParams, kit.system, db, tipsHandler)
  }

  override def route(directives: EclairDirectives): Route = ApiHandlers.registerRoutes(pluginKit, directives)
}

case class TabconfTipsKit(nodeParams: NodeParams, actorSystem: ActorSystem, tipsDb: TipsDb, tipsHandler: ActorRef[TipsHandler.Command])