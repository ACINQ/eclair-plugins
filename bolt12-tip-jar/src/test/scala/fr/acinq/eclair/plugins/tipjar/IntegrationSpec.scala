package fr.acinq.eclair.plugins.tipjar

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.testkit.TestProbe
import com.typesafe.config.{Config, ConfigFactory}
import fr.acinq.bitcoin.scalacompat.SatoshiLong
import fr.acinq.eclair.blockchain.bitcoind.ZmqWatcher
import fr.acinq.eclair.blockchain.bitcoind.ZmqWatcher.{Watch, WatchFundingConfirmed}
import fr.acinq.eclair.channel.{ChannelStateChanged, NORMAL}
import fr.acinq.eclair.payment.PaymentSent
import fr.acinq.eclair.payment.send.OfferPayment
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import fr.acinq.eclair.{MilliSatoshiLong, Setup}

import java.io.File
import java.nio.file.Files
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class IntegrationSpec extends fr.acinq.eclair.integration.IntegrationSpec {
  var offers: Map[String, Offer] = Map()

  override def instantiateEclairNode(name: String, config: Config): Unit = {
    val datadir = new File(INTEGRATION_TMP_DIR, s"datadir-eclair-$name")
    datadir.mkdirs()
    if (useEclairSigner) {
      Files.writeString(datadir.toPath.resolve("eclair-signer.conf"), eclairSignerConf)
    }
    implicit val system: ActorSystem = ActorSystem(s"system-$name", config)

    val plugin = new TipJarPlugin
    val setup = new Setup(datadir, pluginParams = Seq(plugin.params))
    plugin.onSetup(setup)
    val kit = Await.result(setup.bootstrap, 10 seconds)
    plugin.onKit(kit)
    nodes = nodes + (name -> kit)
    offers = offers + (name -> plugin.offer)
  }

  test("Create nodes") {
    instantiateEclairNode("A", ConfigFactory.parseMap(Map("eclair.node-alias" -> "A", "eclair.channel.expiry-delta-blocks" -> 130, "eclair.server.port" -> 29730, "eclair.api.port" -> 28080, "eclair.tip-jar.description" -> "tip to A", "eclair.tip-jar.default-amount-msat" -> 10000, "eclair.tip-jar.max-final-expiry-delta" -> 1000).asJava).withFallback(withDualFunding).withFallback(commonConfig))
    instantiateEclairNode("B", ConfigFactory.parseMap(Map("eclair.node-alias" -> "B", "eclair.channel.expiry-delta-blocks" -> 131, "eclair.server.port" -> 29731, "eclair.api.port" -> 28081, "eclair.tip-jar.description" -> "tip to B", "eclair.tip-jar.default-amount-msat" -> 20000, "eclair.tip-jar.max-final-expiry-delta" -> 2000, "eclair.onion-messages.relay-policy" -> "relay-all").asJava).withFallback(withDualFunding).withFallback(commonConfig))
    instantiateEclairNode("C", ConfigFactory.parseMap(Map("eclair.node-alias" -> "C", "eclair.channel.expiry-delta-blocks" -> 132, "eclair.server.port" -> 29732, "eclair.api.port" -> 28082, "eclair.tip-jar.description" -> "tip to C", "eclair.tip-jar.default-amount-msat" -> 30000, "eclair.tip-jar.max-final-expiry-delta" -> 3000, "eclair.tip-jar.dummy-hops" -> 3, "eclair.tip-jar.intermediate-nodes" -> Seq(nodes("B").nodeParams.nodeId.toHex).asJava).asJava).withFallback(withDualFunding).withFallback(commonConfig))

    val sender = TestProbe()
    val eventListener = TestProbe()
    nodes.values.foreach(_.system.eventStream.subscribe(eventListener.ref, classOf[ChannelStateChanged]))

    connect(nodes("A"), nodes("B"), 20000000 sat, 0 msat)
    connect(nodes("B"), nodes("C"), 10000000 sat, 0 msat)

    val numberOfChannels = 2
    val channelEndpointsCount = 2 * numberOfChannels

    // we make sure all channels have set up their WatchConfirmed for the funding tx
    awaitCond({
      val watches = nodes.values.foldLeft(Set.empty[Watch[_]]) {
        case (watches, setup) =>
          setup.watcher ! ZmqWatcher.ListWatches(sender.ref)
          watches ++ sender.expectMsgType[Set[Watch[_]]]
      }
      watches.count(_.isInstanceOf[WatchFundingConfirmed]) == channelEndpointsCount
    }, max = 20 seconds, interval = 1 second)

    // confirming the funding tx
    generateBlocks(2)

    within(60 seconds) {
      var count = 0
      while (count < channelEndpointsCount) {
        val change = eventListener.expectMsgType[ChannelStateChanged](60 seconds)
        if (change.currentState == NORMAL) count = count + 1
      }
    }
  }

  test("pay offer") {
    val sender = TestProbe("sender")
    val payer = nodes("A")
    val offerPayment = payer.system.spawnAnonymous(OfferPayment(payer.nodeParams, payer.postman, payer.router, payer.paymentInitiator))
    val sendPaymentConfig = OfferPayment.SendPaymentConfig(None, connectDirectly = false, maxAttempts = 1, payer.nodeParams.routerConf.pathFindingExperimentConf.experiments.values.head.getDefaultRouteParams, blocking = true)
    offerPayment ! OfferPayment.PayOffer(sender.ref, offers("C"), 10000 msat, 1, sendPaymentConfig)
    sender.expectMsgType[PaymentSent]
  }
}
