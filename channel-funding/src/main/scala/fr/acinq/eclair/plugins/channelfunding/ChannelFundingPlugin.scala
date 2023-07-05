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

package fr.acinq.eclair.plugins.channelfunding

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.{ClassicActorRefOps, ClassicActorSystemOps}
import akka.actor.typed.{ActorRef, SupervisorStrategy}
import com.typesafe.config.{Config, ConfigFactory}
import fr.acinq.bitcoin.scalacompat.Satoshi
import fr.acinq.eclair.{InterceptOpenChannelCommand, InterceptOpenChannelPlugin, Kit, NodeParams, Plugin, PluginParams, Setup}
import grizzled.slf4j.Logging

import java.io.File


/**
 * Intercept OpenChannel messages received by the node and respond by continuing the process
 * of accepting the request, potentially with different local parameters, or failing the request.
 */
class ChannelFundingPlugin extends Plugin with Logging {
  var pluginKit: OpenChannelInterceptorKit = _
  var config: Config = _

  override def params: PluginParams = new InterceptOpenChannelPlugin {
    // @formatter:off
    override def name: String = "ChannelFundingPlugin"
    // @formatter:on
    override def openChannelInterceptor: ActorRef[InterceptOpenChannelCommand] = pluginKit.openChannelInterceptor
  }

  override def onSetup(setup: Setup): Unit = {
    config = loadConfiguration(setup.datadir)
  }

  override def onKit(kit: Kit): Unit = {
    val minActiveChannels = config.getInt("open-channel-interceptor.min-active-channels")
    val minTotalCapacity = Satoshi(config.getLong("open-channel-interceptor.min-total-capacity"))
    val allowPrivateNodes = config.getBoolean("open-channel-interceptor.allow-private-nodes")
    val openChannelInterceptor = kit.system.spawnAnonymous(Behaviors.supervise(OpenChannelInterceptor(minActiveChannels, minTotalCapacity, allowPrivateNodes, kit.router.toTyped)).onFailure(SupervisorStrategy.restart))
    pluginKit = OpenChannelInterceptorKit(kit.nodeParams, kit.system, openChannelInterceptor)
  }

  /**
   * Order of precedence for the configuration parameters:
   * 1) Java environment variables (-D...)
   * 2) Configuration file channel_funding.conf
   * 3) Default values in reference.conf
   */
  private def loadConfiguration(datadir: File): Config =
    ConfigFactory.systemProperties()
      .withFallback(ConfigFactory.parseFile(new File(datadir, "channel_funding.conf")))
      .withFallback(ConfigFactory.load())
      .resolve()
}

case class OpenChannelInterceptorKit(nodeParams: NodeParams, system: ActorSystem, openChannelInterceptor: ActorRef[InterceptOpenChannelCommand])
