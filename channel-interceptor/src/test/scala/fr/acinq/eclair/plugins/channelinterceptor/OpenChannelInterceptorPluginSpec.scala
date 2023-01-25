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

import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.scalacompat.Satoshi
import org.scalatest.funsuite.AnyFunSuite

class OpenChannelInterceptorPluginSpec extends AnyFunSuite {

  test("check default configuration file") {
    val config = ConfigFactory.load().getConfig("open-channel-interceptor")
    val minActiveChannels = config.getLong("min-active-channels")
    val minTotalCapacity = Satoshi(config.getLong("min-total-capacity"))
    assert(minActiveChannels >= 0)
    assert(minTotalCapacity >= Satoshi(0))
  }

}