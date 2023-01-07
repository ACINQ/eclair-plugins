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
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.scalacompat.Crypto.PrivateKey
import fr.acinq.bitcoin.scalacompat.{ByteVector32, Crypto, DeterministicWallet, SatoshiLong}
import fr.acinq.eclair.blockchain.fee.FeeratePerKw
import fr.acinq.eclair.channel.{ChannelFlags, LocalParams}
import fr.acinq.eclair.wire.protocol.{OpenChannel, OpenDualFundedChannel}
import fr.acinq.eclair.{AcceptOpenChannel, CltvExpiryDelta, Features, InterceptOpenChannelReceived, InterceptOpenChannelResponse, MilliSatoshiLong, RejectOpenChannel, UInt64, randomKey}
import org.scalatest.funsuite.AnyFunSuiteLike
import scodec.bits.ByteVector

class OpenChannelInterceptorSpec extends ScalaTestWithActorTestKit(ConfigFactory.load("application")) with AnyFunSuiteLike {

  val publicKey: Crypto.PublicKey = PrivateKey(ByteVector32.One).publicKey

  test("should intercept and respond to OpenChannelReceived events") {
    val fundingAmount = 1 sat
    val temporaryChannelId = ByteVector32.Zeroes
    val openChannel = OpenChannel(ByteVector32.Zeroes, temporaryChannelId, fundingAmount, 0 msat, 1 sat, UInt64(1), 1 sat, 1 msat, FeeratePerKw(1 sat), CltvExpiryDelta(1), 1, publicKey, publicKey, publicKey, publicKey, publicKey, publicKey, ChannelFlags.Private)
    val openDualChannel = OpenDualFundedChannel(ByteVector32.Zeroes, temporaryChannelId, FeeratePerKw(1 sat), FeeratePerKw(1 sat), fundingAmount, 0 sat, UInt64(0), 0 msat, CltvExpiryDelta(155), 0, 0, publicKey, publicKey, publicKey, publicKey, publicKey, publicKey, ChannelFlags(true))
    val localParams = LocalParams(randomKey().publicKey, DeterministicWallet.KeyPath(Seq(42L)), 1 sat, Long.MaxValue.msat, Some(500 sat), 1 msat, CltvExpiryDelta(144), 50, isInitiator = false, ByteVector.empty, None, Features.empty)

    val openChannelInterceptor = testKit.spawn(OpenChannelInterceptor())
    val peerProbe = TestProbe[InterceptOpenChannelResponse]()

    // approve and continue single funded open channel
    openChannelInterceptor ! InterceptOpenChannelReceived(peerProbe.ref, Left(openChannel), temporaryChannelId, localParams, fundingAmount)
    assert(peerProbe.expectMessageType[AcceptOpenChannel] == AcceptOpenChannel(temporaryChannelId, localParams))

    // fail request to open dual funded channel
    openChannelInterceptor ! InterceptOpenChannelReceived(peerProbe.ref, Right(openDualChannel), temporaryChannelId, localParams, fundingAmount)
    peerProbe.expectMessageType[RejectOpenChannel]
  }
}
