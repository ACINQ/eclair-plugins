/*
 * Copyright 2026 ACINQ SAS
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

package fr.acinq.eclair.plugins.utxovalidator

import akka.actor.ActorSystem
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.{ClassicActorRefOps, ClassicActorSystemOps}
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.scalacompat.{OutPoint, TxOut}
import fr.acinq.eclair.blockchain.OnChainWallet
import fr.acinq.eclair.{Kit, NodeParams, Plugin, PluginParams, Setup, ValidateInteractiveTxPlugin}
import grizzled.slf4j.Logging

import java.io.File
import scala.concurrent.Future

/**
 * Intercept channels that are being opened (or splice transactions being created) and force-close them immediately if
 * they use blacklisted UTXOs.
 */
class UtxoValidatorPlugin extends Plugin with Logging {
  private var pluginKit: UtxoValidatorKit = _
  private var config: UtxoValidatorPluginConfig = _

  override def params: PluginParams = new ValidateInteractiveTxPlugin {
    override def name: String = "UtxoValidatorPlugin"

    override def validateSharedTx(remoteInputs: Map[OutPoint, TxOut], remoteOutputs: Seq[TxOut]): Future[Unit] = {
      if (remoteInputs.keys.exists(outpoint => config.blacklistedUtxos.contains(outpoint))) {
        Future.failed(new IllegalArgumentException("pwned"))
      } else {
        Future.successful(())
      }
    }
  }

  override def onSetup(setup: Setup): Unit = {
    config = loadConfiguration(setup.datadir)
  }

  override def onKit(kit: Kit): Unit = {
    val utxoValidator = kit.system.spawnAnonymous(Behaviors.supervise(UtxoValidator(config, kit.wallet, kit.register.toTyped)).onFailure(SupervisorStrategy.restart))
    pluginKit = UtxoValidatorKit(kit.nodeParams, kit.system, kit.wallet)
  }

  /**
   * Order of precedence for the configuration parameters:
   * 1) Java environment variables (-D...)
   * 2) Configuration file channel_funding.conf
   * 3) Default values in reference.conf
   */
  private def loadConfiguration(datadir: File): UtxoValidatorPluginConfig = {
    val config = ConfigFactory.systemProperties()
      .withFallback(ConfigFactory.parseFile(new File(datadir, "utxo_validator.conf")))
      .withFallback(ConfigFactory.load())
      .resolve()
    UtxoValidatorPluginConfig(config)
  }
}

case class UtxoValidatorKit(nodeParams: NodeParams, system: ActorSystem, wallet: OnChainWallet)
