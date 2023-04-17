package com.getalby.eclair;
import org.junit.jupiter.api.Test;

import akka.actor.ActorRef;
import akka.http.scaladsl.server.RequestContext;
import akka.http.scaladsl.server.RouteResult;
import fr.acinq.bitcoin.PublicKey;
import fr.acinq.bitcoin.scalacompat.ByteVector32;
import fr.acinq.bitcoin.scalacompat.Crypto;
import fr.acinq.eclair.CltvExpiryDelta;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.MilliSatoshi;
import fr.acinq.eclair.Plugin;
import fr.acinq.eclair.PluginParams;
import fr.acinq.eclair.RouteProvider;
import fr.acinq.eclair.Setup;
import fr.acinq.eclair.api.directives.EclairDirectives;
import fr.acinq.eclair.payment.relay.Relayer;
import fr.acinq.eclair.payment.send.PaymentInitiator;
import fr.acinq.eclair.payment.send.PaymentInitiator.SendSpontaneousPayment;
import fr.acinq.eclair.router.Graph;
import fr.acinq.eclair.router.Router;
import fr.acinq.eclair.router.Graph.HeuristicsConstants;
import fr.acinq.eclair.router.Graph.WeightRatios;
import scala.Function1;
import scala.Option;
import scala.concurrent.Future;
import scala.util.Left;
import scala.compat.java8.FutureConverters;


import static akka.http.javadsl.server.Directives.onComplete;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.complete;

import static akka.pattern.Patterns.ask;


public class KeysendActorTest {
	@Test
    public void testCreateKeysendClass() {
        Router.SearchBoundaries boundaries = new Router.SearchBoundaries(new MilliSatoshi(1000L), 5.0, 1, new CltvExpiryDelta(10));
        scala.util.Either<WeightRatios, HeuristicsConstants> heuristics = new Left<>(new Graph.WeightRatios(1.0, 0.0, 0.0, 0.0, new Relayer.RelayFees(new MilliSatoshi(100), 100)));
        Router.MultiPartParams mpp = new Router.MultiPartParams(new MilliSatoshi(1000L), 2);
        Router.RouteParams params = new Router.RouteParams(false,
                boundaries,
                heuristics,
                mpp, "", false);
        //PaymentInitiator.SendSpontaneousPayment ssp = new PaymentInitiator.SendSpontaneousPayment(
        //        new MilliSatoshi(690000L),
        //        new PublicKey(PublicKey.fromHex("037e9d070515c8ba32e6a32fa698e568fc4944a1bb67ae2048947511267202509b")),
        //        ByteVector32.One(),
        //        2,
        //        Option.apply(""),
        //        params,
        //        null,
        //        false
        //);
        PaymentInitiator.SendSpontaneousPayment ssp = new PaymentInitiator.SendSpontaneousPayment(
            new MilliSatoshi(1000),
            new Crypto.PublicKey(PublicKey.fromHex("031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f")),
            ByteVector32.One(),
            1,
            Option.apply(""),
            params,
            null,
            false);
            System.out.println(ssp);
    }
}
