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
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.payment.offer.OfferManager
import fr.acinq.eclair.payment.offer.OfferManager.RegisterOffer
import fr.acinq.eclair.wire.protocol.OfferTypes.Offer
import fr.acinq.eclair.{CltvExpiryDelta, Features, Kit, MilliSatoshi, NodeParams, Plugin, PluginParams, RouteProvider, Setup}
import grizzled.slf4j.Logging

class TipJarPlugin extends Plugin with RouteProvider with Logging {

  private var pluginKit: TipJarKit = _
  private var config: TipJarConfig = _

  override def params: PluginParams = new PluginParams {
    override def name: String = "TipJarPlugin"
  }

  override def onSetup(setup: Setup): Unit = {
    config = TipJarConfig(
      setup.config.getString("tip-jar.description"),
      MilliSatoshi(setup.config.getLong("tip-jar.default-amount-msat")),
      CltvExpiryDelta(setup.config.getInt("tip-jar.max-final-expiry-delta")))
  }

  override def onKit(kit: Kit): Unit = {
    val tipJarHandler = kit.system.spawn(Behaviors.supervise(TipJarHandler(kit.nodeParams.nodeId, config.defaultAmount, config.maxFinalExpiryDelta)).onFailure(SupervisorStrategy.restart), "tip-jar-handler")
    val offer = Offer(None, config.offerDescription, kit.nodeParams.nodeId, Features.empty, kit.nodeParams.chainHash)
    kit.offerManager ! RegisterOffer(offer, kit.nodeParams.privateKey, None, tipJarHandler)
    pluginKit = TipJarKit(kit.nodeParams, offer, kit.system, tipJarHandler)
  }

  override def route(eclairDirectives: EclairDirectives): Route = ApiHandlers.registerRoutes(pluginKit, eclairDirectives)

}

case class TipJarConfig(offerDescription: String, defaultAmount: MilliSatoshi, maxFinalExpiryDelta: CltvExpiryDelta)

case class TipJarKit(nodeParams: NodeParams, offer: Offer, system: ActorSystem, tipJarHandler: ActorRef[OfferManager.HandlerCommand])
