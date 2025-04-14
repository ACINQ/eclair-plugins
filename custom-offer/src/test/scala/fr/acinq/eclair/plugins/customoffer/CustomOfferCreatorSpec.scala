/*
 * Copyright 2025 ACINQ SAS
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

package fr.acinq.eclair.plugins.customoffer

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.scaladsl.adapter.TypedActorRefOps
import fr.acinq.eclair.payment.offer.OfferManager
import fr.acinq.eclair.plugins.customoffer.CustomOfferCreator.{CreateOffer, CreateOfferResult, CreatedOffer}
import fr.acinq.eclair.router.Router
import fr.acinq.eclair.wire.protocol.OfferTypes
import fr.acinq.eclair.{CltvExpiryDelta, EncodedNodeId, MilliSatoshiLong, TestConstants, randomKey}
import org.scalatest.funsuite.AnyFunSuiteLike

class CustomOfferCreatorSpec extends ScalaTestWithActorTestKit with AnyFunSuiteLike {
  test("create offer") {
    val nodeParams = TestConstants.Alice.nodeParams
    val config = CustomOfferConfig(Set(randomKey().publicKey), CltvExpiryDelta(123))
    val router = TestProbe[Any]()
    val offerManager = TestProbe[OfferManager.Command]()
    val customOfferCreator = testKit.spawn(CustomOfferCreator(nodeParams, config, router.ref.toClassic, offerManager.ref))

    val probe = TestProbe[CreateOfferResult]()
    val introduction = randomKey().publicKey
    customOfferCreator ! CreateOffer(probe.ref, 456000 msat, "custom offer", introduction)

    val routeRequest = router.expectMessageType[Router.MessageRouteRequest]
    routeRequest.replyTo ! Router.MessageRoute(Seq(randomKey().publicKey), routeRequest.target)

    val offer = probe.expectMessageType[CreatedOffer].offer
    assert(offer.contactInfos.head.asInstanceOf[OfferTypes.BlindedPath].route.firstNodeId == EncodedNodeId(introduction))
  }
}
