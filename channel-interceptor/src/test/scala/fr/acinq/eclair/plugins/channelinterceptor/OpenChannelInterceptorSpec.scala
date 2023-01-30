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

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.ClassicActorRefOps
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.scalacompat.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin.scalacompat.{ByteVector32, Crypto, Satoshi, SatoshiLong}
import fr.acinq.eclair.blockchain.fee.FeeratePerKw
import fr.acinq.eclair.channel.ChannelFlags
import fr.acinq.eclair.io.OpenChannelInterceptor.{DefaultParams, OpenChannelNonInitiator}
import fr.acinq.eclair.io.Peer
import fr.acinq.eclair.io.Peer.ChannelId
import fr.acinq.eclair.router.Router.{GetNode, PublicNode, UnknownNode}
import fr.acinq.eclair.wire.protocol._
import fr.acinq.eclair.{AcceptOpenChannel, CltvExpiryDelta, Features, InterceptOpenChannelCommand, InterceptOpenChannelReceived, InterceptOpenChannelResponse, MilliSatoshiLong, RejectOpenChannel, TestConstants, TimestampSecondLong, UInt64, randomBytes32, randomBytes64}
import org.scalatest.Outcome
import org.scalatest.funsuite.FixtureAnyFunSuiteLike
import scodec.bits.ByteVector

class OpenChannelInterceptorSpec extends ScalaTestWithActorTestKit(ConfigFactory.load("application")) with FixtureAnyFunSuiteLike {
  val publicKey: Crypto.PublicKey = PrivateKey(ByteVector32.One).publicKey
  val fundingAmount: Satoshi = 1 sat
  val remoteNodeId: Crypto.PublicKey = PrivateKey(ByteVector32.One).publicKey
  val temporaryChannelId: ByteVector32 = ByteVector32.Zeroes
  val openChannel: OpenChannel = OpenChannel(ByteVector32.Zeroes, temporaryChannelId, fundingAmount, 0 msat, 1 sat, UInt64(1), 1 sat, 1 msat, FeeratePerKw(1 sat), CltvExpiryDelta(1), 1, publicKey, publicKey, publicKey, publicKey, publicKey, publicKey, ChannelFlags.Private)
  val openDualChannel: OpenDualFundedChannel = OpenDualFundedChannel(ByteVector32.Zeroes, ByteVector32.One, FeeratePerKw(5000 sat), FeeratePerKw(4000 sat), 250_000 sat, 500 sat, UInt64(50_000), 15 msat, CltvExpiryDelta(144), 483, 650_000, publicKey(1), publicKey(2), publicKey(3), publicKey(4), publicKey(5), publicKey(6), publicKey(7), ChannelFlags(true))
  val defaultParams: DefaultParams = DefaultParams(100 sat, 100000 msat, 100 msat, CltvExpiryDelta(288), 10)
  val channels: Map[Peer.FinalChannelId, actor.ActorRef] = Map(Peer.FinalChannelId(randomBytes32()) -> system.classicSystem.deadLetters)
  val connectedData: Peer.ConnectedData = Peer.ConnectedData(NodeAddress.fromParts("1.2.3.4", 42000).get, system.classicSystem.deadLetters, Init(TestConstants.Alice.nodeParams.features.initFeatures()), Init(TestConstants.Bob.nodeParams.features.initFeatures()), channels.map { case (k: ChannelId, v) => (k, v) })
  val openChannelNonInitiator: OpenChannelNonInitiator = OpenChannelNonInitiator(remoteNodeId, Left(openChannel), connectedData.localFeatures, connectedData.remoteFeatures, connectedData.peerConnection.toTyped)
  val bobAnnouncement: NodeAnnouncement = announcement(TestConstants.Bob.nodeParams.nodeId)

  def publicKey(fill: Byte): PublicKey = PrivateKey(ByteVector.fill(32)(fill)).publicKey
  def announcement(nodeId: PublicKey): NodeAnnouncement = NodeAnnouncement(randomBytes64(), Features.empty, 1 unixsec, nodeId, Color(100.toByte, 200.toByte, 300.toByte), "node-alias", NodeAddress.fromParts("1.2.3.4", 42000).get :: Nil)

  case class FixtureParam(router: TestProbe[Any], peer: TestProbe[InterceptOpenChannelResponse], openChannelInterceptor: ActorRef[InterceptOpenChannelCommand])

  override def withFixture(test: OneArgTest): Outcome = {
    val router = TestProbe[Any]()
    val peer = TestProbe[InterceptOpenChannelResponse]()
    val openChannelInterceptor = testKit.spawn(OpenChannelInterceptor(2, 10000 sat, router.ref))

    withFixture(test.toNoArgTest(FixtureParam(router, peer, openChannelInterceptor)))
  }

  test("should intercept and respond to OpenChannelReceived events") { f =>
    import f._

    // approve and continue request from public peer
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannelNonInitiator, defaultParams)
    router.expectMessageType[GetNode].replyTo ! PublicNode(bobAnnouncement, 2, 10000 sat)
    assert(peer.expectMessageType[AcceptOpenChannel] == AcceptOpenChannel(temporaryChannelId, defaultParams))

    // fail request from private peer
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannelNonInitiator, defaultParams)
    router.expectMessageType[GetNode].replyTo ! UnknownNode(remoteNodeId)
    assert(peer.expectMessageType[RejectOpenChannel].error.toAscii.contains("no public channels"))

    // fail request from public peer with too low capacity
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannelNonInitiator, defaultParams)
    router.expectMessageType[GetNode].replyTo ! PublicNode(bobAnnouncement, 2, 9999 sat)
    assert(peer.expectMessageType[RejectOpenChannel].error.toAscii.contains("total capacity"))

    // fail request from public peer with too few channels
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannelNonInitiator, defaultParams)
    router.expectMessageType[GetNode].replyTo ! PublicNode(bobAnnouncement, 1, 10000 sat)
    assert(peer.expectMessageType[RejectOpenChannel].error.toAscii.contains("active channels"))
  }
}
