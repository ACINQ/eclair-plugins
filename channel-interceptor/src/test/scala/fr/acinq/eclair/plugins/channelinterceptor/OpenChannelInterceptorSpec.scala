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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.eventstream.EventStream
import akka.actor.typed.scaladsl.adapter.TypedActorRefOps
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.scalacompat.Crypto.PrivateKey
import fr.acinq.bitcoin.scalacompat.{ByteVector32, Crypto, DeterministicWallet, SatoshiLong}
import fr.acinq.eclair.TestConstants.{Alice, Bob}
import fr.acinq.eclair.blockchain.fee.FeeratePerKw
import fr.acinq.eclair.channel.{ChannelConfig, ChannelFlags, ChannelTypes, LocalParams}
import fr.acinq.eclair.io.Peer.{OutgoingMessage, SpawnChannelNonInitiator}
import fr.acinq.eclair.io.{ConnectionInfo, OpenChannelReceived}
import fr.acinq.eclair.wire.protocol.{Init, NodeAddress, OpenChannel, OpenDualFundedChannel}
import fr.acinq.eclair.{CltvExpiryDelta, Features, MilliSatoshiLong, UInt64, randomKey}
import org.scalatest.funsuite.AnyFunSuiteLike
import scodec.bits.ByteVector

class OpenChannelInterceptorSpec extends ScalaTestWithActorTestKit(ConfigFactory.load("application")) with AnyFunSuiteLike {

  val publicKey: Crypto.PublicKey = PrivateKey(ByteVector32.One).publicKey

  test("should intercept and respond to OpenChannelReceived events") {
    val openChannel = OpenChannel(ByteVector32.Zeroes, ByteVector32.Zeroes, 0 sat, 0 msat, 1 sat, UInt64(1), 1 sat, 1 msat, FeeratePerKw(1 sat), CltvExpiryDelta(1), 1, publicKey, publicKey, publicKey, publicKey, publicKey, publicKey, ChannelFlags.Private)
    val openDualChannel = OpenDualFundedChannel(ByteVector32.Zeroes, ByteVector32.One, FeeratePerKw(1 sat), FeeratePerKw(1 sat), 0 sat, 0 sat, UInt64(0), 0 msat, CltvExpiryDelta(155), 0, 0, publicKey, publicKey, publicKey, publicKey, publicKey, publicKey, ChannelFlags(true))
    val localParams = LocalParams(randomKey().publicKey, DeterministicWallet.KeyPath(Seq(42L)), 1 sat, Long.MaxValue.msat, Some(500 sat), 1 msat, CltvExpiryDelta(144), 50, isInitiator = false, ByteVector.empty, None, Features.empty)

    testKit.spawn(OpenChannelInterceptor())
    val peerProbe = TestProbe[Any]()
    val connectionInfo = ConnectionInfo(NodeAddress.fromParts("1.2.3.4", 9735).get, peerProbe.ref.toClassic, Init(Alice.nodeParams.features.initFeatures()), Init(Bob.nodeParams.features.initFeatures()))

    // approve and continue single funded open channel
    testKit.system.eventStream ! EventStream.Publish(OpenChannelReceived(peerProbe.ref.toClassic, Left(openChannel), ChannelTypes.Standard(), localParams, connectionInfo))
    assert(peerProbe.expectMessageType[SpawnChannelNonInitiator] == SpawnChannelNonInitiator(Left(openChannel), ChannelConfig.standard, ChannelTypes.Standard(), localParams))

    // fail request to open dual funded channel
    testKit.system.eventStream ! EventStream.Publish(OpenChannelReceived(peerProbe.ref.toClassic, Right(openDualChannel), ChannelTypes.Standard(), localParams, connectionInfo))
    peerProbe.expectMessageType[OutgoingMessage]
  }
}
