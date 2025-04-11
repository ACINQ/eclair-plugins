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

import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.adapter.ClassicSchedulerOps
import akka.http.scaladsl.server.Route
import fr.acinq.bitcoin.scalacompat.Crypto.PublicKey
import fr.acinq.eclair.MilliSatoshi
import fr.acinq.eclair.api.directives.EclairDirectives
import fr.acinq.eclair.plugins.customoffer.CustomOfferCreator.{CreateOffer, CreateOfferResult}
import fr.acinq.eclair.api.serde.FormParamExtractors._

object ApiHandlers {

  def registerRoutes(kit: CustomOfferKit, eclairDirectives: EclairDirectives): Route = {
    import eclairDirectives._
    import fr.acinq.eclair.api.serde.JsonSupport.{formats, marshaller, serialization}

    val customoffer: Route = postRequest("customoffer") { implicit t =>
      formFields("amount".as[MilliSatoshi], "description", "introductionnode".as[PublicKey]) { (amount, description, introductionNode) =>
        implicit val scheduler: Scheduler = kit.system.scheduler.toTyped
        complete(kit.customOfferCreator.ask[CreateOfferResult](r => CreateOffer(r, amount, description, introductionNode)))
      }
    }

    customoffer
  }
}
