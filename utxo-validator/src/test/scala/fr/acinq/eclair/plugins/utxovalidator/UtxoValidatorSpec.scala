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

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.scalacompat.{OutPoint, SatoshiLong, Script, Transaction, TxId, TxIn, TxOut}
import fr.acinq.eclair.TestUtils.randomTxId
import fr.acinq.eclair.blockchain.SingleKeyOnChainWallet
import fr.acinq.eclair.channel.{CMD_FORCECLOSE, ChannelFundingCreated, Register}
import fr.acinq.eclair.plugins.utxovalidator.UtxoValidator.WrappedChannelFundingCreated
import fr.acinq.eclair.{randomBytes32, randomKey}
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class UtxoValidatorSpec extends ScalaTestWithActorTestKit(ConfigFactory.load("application")) with AnyFunSuiteLike {

  private val config = UtxoValidatorPluginConfig(
    blacklistedUtxos = Set(
      OutPoint(TxId.fromValidHex("2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a"), 2),
      OutPoint(TxId.fromValidHex("0303030303030303030303030303030303030303030303030303030303030303"), 0)
    ),
    fetchTxFrequency = 100 millis,
  )

  class DummyWallet extends SingleKeyOnChainWallet {
    private var transactions = Seq.empty[Transaction]

    def addTransaction(tx: Transaction): Unit = {
      transactions = transactions :+ tx
    }

    override def getTransaction(txId: TxId)(implicit ec: ExecutionContext): Future[Transaction] = {
      transactions.find(_.txid == txId) match {
        case Some(tx) => Future.successful(tx)
        case None => Future.failed(new IllegalArgumentException("transaction cannot be found"))
      }
    }
  }

  test("force-close channels that contain blacklisted utxos") {
    val wallet = new DummyWallet()
    val register = TestProbe[Any]()
    val validator = testKit.spawn(UtxoValidator(config, wallet, register.ref))

    val blacklistedTx = Transaction(
      version = 2,
      txIn = Seq(TxIn(OutPoint(randomTxId(), 1), Nil, 0), TxIn(config.blacklistedUtxos.head, Nil, 0), TxIn(OutPoint(randomTxId(), 5), Nil, 0)),
      txOut = Seq(TxOut(100_000 sat, Script.pay2wpkh(randomKey().publicKey))),
      lockTime = 0
    )

    // If we have the transaction, we can immediately validate it without querying bitcoind.
    val channelId = randomBytes32()
    validator ! WrappedChannelFundingCreated(ChannelFundingCreated(null, channelId, randomKey().publicKey, Right(blacklistedTx), 0, null))
    assert(register.expectMessageType[Register.Forward[CMD_FORCECLOSE]].channelId == channelId)

    // Otherwise, we use bitcoind to fetch the corresponding transaction: note we don't provide the transaction
    // immediately, which shows that we will retry until we're able to fetch it.
    validator ! WrappedChannelFundingCreated(ChannelFundingCreated(null, channelId, randomKey().publicKey, Left(blacklistedTx.txid), 0, null))
    register.expectNoMessage(500 millis)
    wallet.addTransaction(blacklistedTx)
    assert(register.expectMessageType[Register.Forward[CMD_FORCECLOSE]].channelId == channelId)
  }

  test("skip channels that don't contain blacklisted utxos") {
    val wallet = new DummyWallet()
    val register = TestProbe[Any]()
    val validator = testKit.spawn(UtxoValidator(config, wallet, register.ref))

    val validTx = Transaction(
      version = 2,
      txIn = Seq(TxIn(OutPoint(randomTxId(), 1), Nil, 0), TxIn(OutPoint(randomTxId(), 5), Nil, 0)),
      txOut = Seq(TxOut(100_000 sat, Script.pay2wpkh(randomKey().publicKey))),
      lockTime = 0
    )
    wallet.addTransaction(validTx)

    validator ! WrappedChannelFundingCreated(ChannelFundingCreated(null, randomBytes32(), randomKey().publicKey, Right(validTx), 0, null))
    register.expectNoMessage(100 millis)
    validator ! WrappedChannelFundingCreated(ChannelFundingCreated(null, randomBytes32(), randomKey().publicKey, Left(validTx.txid), 0, null))
    register.expectNoMessage(100 millis)
  }

}