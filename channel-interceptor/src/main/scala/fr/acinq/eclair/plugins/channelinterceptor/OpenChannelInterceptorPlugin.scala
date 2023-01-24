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

package fr.acinq.eclair.plugins.channelinterceptor

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.{ClassicActorRefOps, ClassicActorSystemOps}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import fr.acinq.bitcoin.scalacompat.SatoshiLong
import fr.acinq.eclair.plugins.channelinterceptor.OpenChannelInterceptor.WrappedInterceptOpenChannelReceived
import fr.acinq.eclair.{InterceptOpenChannelPlugin, InterceptOpenChannelReceived, Kit, NodeParams, Plugin, PluginParams, Setup}
import grizzled.slf4j.Logging


/**
 * Intercept OpenChannel messages received by the node and respond by continuing the process
 * of accepting the request, potentially with different local parameters, or failing the request.
 */

object OpenChannelInterceptorPlugin {
  def apply(openChannelInterceptor: ActorRef[OpenChannelInterceptor.Command]): Behavior[InterceptOpenChannelReceived] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        interceptOpenChannelReceived =>
          openChannelInterceptor ! WrappedInterceptOpenChannelReceived(interceptOpenChannelReceived)
          Behaviors.same
      }
    }
}

class OpenChannelInterceptorPlugin extends Plugin with Logging {
  var pluginKit: OpenChannelInterceptorKit = _

  override def params: PluginParams = new InterceptOpenChannelPlugin {
    // @formatter:off
    override def name: String = "OpenChannelInterceptorPlugin"
    // @formatter:on
    override def openChannelInterceptor: ActorRef[InterceptOpenChannelReceived] = pluginKit.openChannelInterceptor
  }

  override def onSetup(setup: Setup): Unit = {
  }

  override def onKit(kit: Kit): Unit = {
    val openChannelInterceptor = kit.system.spawnAnonymous(Behaviors.supervise(OpenChannelInterceptor(10, 10000000 sat, kit.router.toTyped)).onFailure(SupervisorStrategy.restart))

    val plugin = kit.system.spawn(Behaviors.supervise(OpenChannelInterceptorPlugin(openChannelInterceptor)).onFailure(SupervisorStrategy.restart), "open-channel-interceptor-plugin")

    pluginKit = OpenChannelInterceptorKit(kit.nodeParams, kit.system, plugin)
  }
}

case class OpenChannelInterceptorKit(nodeParams: NodeParams, system: ActorSystem, openChannelInterceptor: ActorRef[InterceptOpenChannelReceived])
