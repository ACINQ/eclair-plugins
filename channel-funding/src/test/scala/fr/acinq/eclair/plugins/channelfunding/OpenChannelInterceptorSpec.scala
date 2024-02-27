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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.scalacompat.SatoshiLong
import fr.acinq.eclair.io.OpenChannelInterceptor.{DefaultParams, OpenChannelNonInitiator}
import fr.acinq.eclair.io.PeerSpec.{createOpenChannelMessage, createOpenDualFundedChannelMessage}
import fr.acinq.eclair.router.Router.{GetNode, PublicNode, UnknownNode}
import fr.acinq.eclair.wire.protocol._
import fr.acinq.eclair.{AcceptOpenChannel, CltvExpiryDelta, Features, InterceptOpenChannelCommand, InterceptOpenChannelReceived, InterceptOpenChannelResponse, MilliSatoshiLong, RejectOpenChannel, TimestampSecondLong, randomBytes64, randomKey}
import org.scalatest.funsuite.FixtureAnyFunSuiteLike
import org.scalatest.{Outcome, Tag}

import scala.concurrent.duration.DurationInt

class OpenChannelInterceptorSpec extends ScalaTestWithActorTestKit(ConfigFactory.load("application")) with FixtureAnyFunSuiteLike {
  val remoteNodeId = randomKey().publicKey
  val whitelistedRemoteNodeId = randomKey().publicKey
  val peerAddress = NodeAddress.fromParts("127.0.0.1", 9735).get
  val defaultParams = DefaultParams(100 sat, 100000 msat, 100 msat, CltvExpiryDelta(288), 10)
  val openChannel = OpenChannelNonInitiator(remoteNodeId, Left(createOpenChannelMessage()), Features.empty, Features.empty, TestProbe[Any]().ref, peerAddress)
  val announcement = NodeAnnouncement(randomBytes64(), Features.empty, 1 unixsec, remoteNodeId, Color(100.toByte, 200.toByte, 300.toByte), "node-alias", NodeAddress.fromParts("1.2.3.4", 42000).get :: Nil)

  case class FixtureParam(router: TestProbe[Any], peer: TestProbe[InterceptOpenChannelResponse], openChannelInterceptor: ActorRef[InterceptOpenChannelCommand])

  override def withFixture(test: OneArgTest): Outcome = {
    val router = TestProbe[Any]()
    val peer = TestProbe[InterceptOpenChannelResponse]()
    val config = ChannelFundingPluginConfig(
      RemoteNodeRequirements(
        whitelist = Set(whitelistedRemoteNodeId),
        minActiveChannels = 2,
        minTotalCapacity = 10_000 sat,
        rejectPrivateNodes = test.tags.contains("no-private-peers")
      ),
      DualFundingLiquidityPolicy(
        whitelist = Set(whitelistedRemoteNodeId),
        fundingAmount = 200_000 sat,
      )
    )
    val openChannelInterceptor = testKit.spawn(OpenChannelInterceptor(config, router.ref))
    withFixture(test.toNoArgTest(FixtureParam(router, peer, openChannelInterceptor)))
  }

  test("approve channel requests") { f =>
    import f._

    // request from public peer
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannel, defaultParams)
    router.expectMessageType[GetNode].replyTo ! PublicNode(announcement, 2, 10000 sat)
    assert(peer.expectMessageType[AcceptOpenChannel] == AcceptOpenChannel(openChannel.temporaryChannelId, defaultParams, None))

    // request from private peer
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannel, defaultParams)
    router.expectMessageType[GetNode].replyTo ! UnknownNode(remoteNodeId)
    assert(peer.expectMessageType[AcceptOpenChannel] == AcceptOpenChannel(openChannel.temporaryChannelId, defaultParams, None))
  }

  test("approve and contribute to dual-funded channel request") { f =>
    import f._

    val openChannelDualFunded = OpenChannelNonInitiator(whitelistedRemoteNodeId, Right(createOpenDualFundedChannelMessage()), Features.empty, Features.empty, TestProbe[Any]().ref, peerAddress)
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannelDualFunded, defaultParams)
    router.expectNoMessage(100 millis) // we don't check requirements for whitelisted nodes
    val accept = peer.expectMessageType[AcceptOpenChannel]
    assert(accept.temporaryChannelId == openChannelDualFunded.temporaryChannelId)
    assert(accept.localFundingAmount_opt.contains(200_000 sat))
  }

  test("reject channel requests", Tag("no-private-peers")) { f =>
    import f._

    // from public peer with too low capacity
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannel, defaultParams)
    router.expectMessageType[GetNode].replyTo ! PublicNode(announcement, 2, 9999 sat)
    assert(peer.expectMessageType[RejectOpenChannel].error.toAscii.contains("total capacity"))

    // from public peer with too few channels
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannel, defaultParams)
    router.expectMessageType[GetNode].replyTo ! PublicNode(announcement, 1, 10000 sat)
    assert(peer.expectMessageType[RejectOpenChannel].error.toAscii.contains("active channels"))

    // from private peer
    openChannelInterceptor ! InterceptOpenChannelReceived(peer.ref, openChannel, defaultParams)
    router.expectMessageType[GetNode].replyTo ! UnknownNode(remoteNodeId)
    assert(peer.expectMessageType[RejectOpenChannel].error.toAscii.contains("no public channels"))
  }
}
