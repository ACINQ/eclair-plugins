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

import com.typesafe.config.Config
import fr.acinq.bitcoin.scalacompat.Crypto.PublicKey
import fr.acinq.bitcoin.scalacompat.Satoshi
import scodec.bits.ByteVector

import scala.jdk.CollectionConverters.CollectionHasAsScala

/**
 * @param whitelist         nodes from this whitelist will be allowed even if they don't meet requirements.
 * @param minActiveChannels minimum number of public channels the remote node must have.
 * @param minTotalCapacity  minimum total capacity of existing public channels the remote node must have.
 * @param allowPrivateNodes if true, we allow channels from private nodes (who have no public channels yet).
 */
case class RemoteNodeRequirements(whitelist: Set[PublicKey], minActiveChannels: Int, minTotalCapacity: Satoshi, allowPrivateNodes: Boolean) {
  def isWhitelisted(nodeId: PublicKey): Boolean = whitelist.contains(nodeId)
}

case class DualFundingLiquidityPolicy(whitelist: Set[PublicKey], fundingAmount: Satoshi) {
  def fundingAmountFor(nodeId: PublicKey): Option[Satoshi] = if (whitelist.contains(nodeId)) Some(fundingAmount) else None
}

case class ChannelFundingPluginConfig(remoteNodeRequirements: RemoteNodeRequirements, fundingPolicy: DualFundingLiquidityPolicy)

object ChannelFundingPluginConfig {
  def apply(config: Config): ChannelFundingPluginConfig = {
    ChannelFundingPluginConfig(
      RemoteNodeRequirements(
        whitelist = config.getStringList("channel-funding.remote-node-requirements.peer-whitelist").asScala.map(s => PublicKey(ByteVector.fromValidHex(s), checkValid = true)).toSet,
        minActiveChannels = config.getInt("channel-funding.remote-node-requirements.min-active-channels"),
        minTotalCapacity = Satoshi(config.getLong("channel-funding.remote-node-requirements.min-total-capacity-sat")),
        allowPrivateNodes = config.getBoolean("channel-funding.remote-node-requirements.allow-private-nodes"),
      ),
      DualFundingLiquidityPolicy(
        whitelist = config.getStringList("channel-funding.dual-funding-liquidity-policy.peer-whitelist").asScala.map(s => PublicKey(ByteVector.fromValidHex(s), checkValid = true)).toSet,
        fundingAmount = Satoshi(config.getLong("channel-funding.dual-funding-liquidity-policy.local-funding-amount-sat")),
      )
    )
  }
}
