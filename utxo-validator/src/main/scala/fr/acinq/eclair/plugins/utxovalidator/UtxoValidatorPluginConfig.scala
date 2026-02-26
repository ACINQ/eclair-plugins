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

import com.typesafe.config.Config
import fr.acinq.bitcoin.scalacompat.OutPoint

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.CollectionHasAsScala

case class UtxoValidatorPluginConfig(blacklistedUtxos: Set[OutPoint], fetchTxFrequency: FiniteDuration)

object UtxoValidatorPluginConfig {
  def apply(config: Config): UtxoValidatorPluginConfig = {
    UtxoValidatorPluginConfig(
      blacklistedUtxos = config.getStringList("utxo-validator.blacklisted-utxos").asScala.map(OutPoint.read).toSet,
      fetchTxFrequency = FiniteDuration(config.getDuration("utxo-validator.fetch-tx-frequency").getSeconds, TimeUnit.SECONDS),
    )
  }
}
