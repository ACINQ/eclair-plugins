/*
 * Copyright 2023 ACINQ SAS
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

package fr.acinq.eclair.plugins.tipjar

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, SupervisorStrategy}
import akka.http.scaladsl.server.Route
import fr.acinq.bitcoin.scalacompat.Crypto.PublicKey
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.message.OnionMessages
import fr.acinq.eclair.message.OnionMessages.{IntermediateNode, Recipient}
import fr.acinq.eclair.payment.offer.OfferManager
import fr.acinq.eclair.payment.offer.OfferManager.RegisterOffer
import fr.acinq.eclair.payment.receive.MultiPartHandler.{DummyBlindedHop, ReceivingRoute}
import fr.acinq.eclair.wire.protocol.OfferTypes
import fr.acinq.eclair.wire.protocol.OfferTypes.{Offer, OfferPaths, OfferTlv}
import fr.acinq.eclair.{CltvExpiryDelta, Features, Kit, MilliSatoshi, NodeParams, Plugin, PluginParams, RouteProvider, Setup, randomBytes32, randomKey}
import grizzled.slf4j.Logging
import scodec.bits.ByteVector

import scala.jdk.CollectionConverters.ListHasAsScala

class TipJarPlugin extends Plugin with RouteProvider with Logging {

  private var pluginKit: TipJarKit = _
  private var config: TipJarConfig = _

  override def params: PluginParams = new PluginParams {
    override def name: String = "TipJarPlugin"
  }

  override def onSetup(setup: Setup): Unit = {
    val intermediateNodes = if (setup.config.hasPath("tip-jar.intermediate-nodes")) {
      setup.config.getStringList("tip-jar.intermediate-nodes").asScala.toSeq.map(s => PublicKey(ByteVector.fromValidHex(s)))
    }
    else {
      Nil
    }
    val dummyHops = if (setup.config.hasPath("tip-jar.dummy-hops")) {
      setup.config.getInt("tip-jar.dummy-hops")
    }
    else {
      0
    }
    config = TipJarConfig(
      setup.config.getString("tip-jar.description"),
      MilliSatoshi(setup.config.getLong("tip-jar.default-amount-msat")),
      CltvExpiryDelta(setup.config.getInt("tip-jar.max-final-expiry-delta")),
      intermediateNodes,
      dummyHops)
  }

  override def onKit(kit: Kit): Unit = {
    require(kit.nodeParams.features.hasFeature(Features.RouteBlinding))
    require(kit.nodeParams.features.hasFeature(Features.OnionMessages))
    val channelFees = kit.nodeParams.relayParams.publicChannelFees
    val dummyHops = Seq.fill(config.dummyHops)(DummyBlindedHop(channelFees.feeBase, channelFees.feeProportionalMillionths, kit.nodeParams.channelConf.expiryDelta))
    val route = ReceivingRoute(config.intermediateNodes :+ kit.nodeParams.nodeId, config.maxFinalExpiryDelta, dummyHops)
    val tipJarHandler = kit.system.spawn(Behaviors.supervise(TipJarHandler(route, config.defaultAmount)).onFailure(SupervisorStrategy.restart), "tip-jar-handler")
    val (blindingRouteTlvs, pathId_opt, key) = if (config.intermediateNodes.nonEmpty || config.dummyHops > 0) {
      val pathId = randomBytes32()
      val path = OnionMessages.buildRoute(
        randomKey(),
        config.intermediateNodes.map(IntermediateNode(_)) ++ Seq.fill(config.dummyHops)(IntermediateNode(kit.nodeParams.nodeId)),
        Recipient(kit.nodeParams.nodeId, Some(pathId)))
      (Set[OfferTlv](OfferPaths(Seq(OfferTypes.BlindedPath(path)))), Some(pathId), randomKey())
    } else {
      (Set.empty[OfferTlv], None, kit.nodeParams.privateKey)
    }
    val offer = Offer(None, config.offerDescription, key.publicKey, Features.empty, kit.nodeParams.chainHash, additionalTlvs = blindingRouteTlvs)
    kit.offerManager ! RegisterOffer(offer, key, pathId_opt, tipJarHandler)
    pluginKit = TipJarKit(kit.nodeParams, offer, kit.system, tipJarHandler)
  }

  override def route(eclairDirectives: EclairDirectives): Route = ApiHandlers.registerRoutes(pluginKit, eclairDirectives)

}

case class TipJarConfig(offerDescription: String, defaultAmount: MilliSatoshi, maxFinalExpiryDelta: CltvExpiryDelta, intermediateNodes: Seq[PublicKey], dummyHops: Int)

case class TipJarKit(nodeParams: NodeParams, offer: Offer, system: ActorSystem, tipJarHandler: ActorRef[OfferManager.HandlerCommand])
