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
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorRef, SupervisorStrategy}
import fr.acinq.eclair.InterceptedMessageType.InterceptOpenChannel
import fr.acinq.eclair.{InterceptMessagePlugin, InterceptedMessageType, Kit, NodeParams, Plugin, PluginParams, Setup}
import grizzled.slf4j.Logging

/**
 * Intercept OpenChannel messages received by the node and respond by continuing the process
 * of accepting the request, potentially with different local parameters, or failing the request.
 */

class ChannelInterceptorPlugin extends Plugin with Logging {
  var pluginKit: ChannelInterceptorKit = _

  override def params: PluginParams = new InterceptMessagePlugin {
    // @formatter:off
    override def name: String = "ChannelInterceptorPlugin"
    override def canIntercept: Set[InterceptedMessageType.Value] = Set(InterceptOpenChannel)
    // @formatter:on
  }

  override def onSetup(setup: Setup): Unit = {
  }

  override def onKit(kit: Kit): Unit = {
    val openChannelInterceptor = kit.system.spawn(Behaviors.supervise(OpenChannelInterceptor()).onFailure(SupervisorStrategy.restart), "open-channel-interceptor")
    pluginKit = ChannelInterceptorKit(kit.nodeParams, kit.system, openChannelInterceptor)
  }
}

case class ChannelInterceptorKit(nodeParams: NodeParams, system: ActorSystem, openChannelInterceptor: ActorRef[OpenChannelInterceptor.Command])
