/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.eclair.plugins.customoffer

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, SupervisorStrategy}
import akka.http.scaladsl.server.Route
import fr.acinq.bitcoin.scalacompat.Crypto.PublicKey
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.{CltvExpiryDelta, Features, Kit, Plugin, PluginParams, RouteProvider, Setup}
import grizzled.slf4j.Logging
import scodec.bits.ByteVector

import scala.jdk.CollectionConverters.CollectionHasAsScala

class CustomOfferPlugin extends Plugin with RouteProvider with Logging {

  private var pluginKit: CustomOfferKit = _
  private var config: CustomOfferConfig = _

  override def params: PluginParams = new PluginParams {
    override def name: String = "CustomOfferPlugin"
  }

  override def onSetup(setup: Setup): Unit = {
    // Read the plugin's config from eclair.conf
    config = CustomOfferConfig(
      setup.config.getStringList("custom-offer.avoid-nodes").asScala.map(s => PublicKey(ByteVector.fromValidHex(s))).toSet,
      CltvExpiryDelta(setup.config.getInt("custom-offer.max-final-expiry-delta")),
    )
  }

  override def onKit(kit: Kit): Unit = {
    require(kit.nodeParams.features.hasFeature(Features.RouteBlinding))
    require(kit.nodeParams.features.hasFeature(Features.OnionMessages))
    val customOfferCreator = kit.system.spawn(Behaviors.supervise(CustomOfferCreator(kit.nodeParams, config, kit.router, kit.offerManager)).onFailure(SupervisorStrategy.restart), "custom-offer-creator")
    pluginKit = CustomOfferKit(kit.system, customOfferCreator)
  }

  override def route(eclairDirectives: EclairDirectives): Route = ApiHandlers.registerRoutes(pluginKit, eclairDirectives)
}

case class CustomOfferConfig(avoidNodes: Set[PublicKey], maxFinalExpiryDelta: CltvExpiryDelta)

case class CustomOfferKit(system: ActorSystem, customOfferCreator: ActorRef[CustomOfferCreator.Command])
