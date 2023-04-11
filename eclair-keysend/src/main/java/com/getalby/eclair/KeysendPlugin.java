package com.getalby.eclair;


import akka.actor.ActorRef;
import akka.http.scaladsl.server.RequestContext;
import akka.http.scaladsl.server.RouteResult;
import fr.acinq.bitcoin.PublicKey;
import fr.acinq.bitcoin.scalacompat.ByteVector32;
import fr.acinq.bitcoin.scalacompat.Crypto;
import fr.acinq.eclair.*;
import fr.acinq.eclair.api.directives.EclairDirectives;
import fr.acinq.eclair.payment.send.PaymentInitiator;
import scala.Function1;
import scala.Option;
import scala.concurrent.Future;
import scala.compat.java8.FutureConverters;


import static akka.http.javadsl.server.Directives.onComplete;
import static akka.http.javadsl.server.Directives.path;
import static akka.http.javadsl.server.Directives.complete;

import static akka.pattern.Patterns.ask;

public class KeysendPlugin implements Plugin, RouteProvider {

    private Kit kit;
    private ActorRef keySendActor;

    @Override
    public PluginParams params() {
        return () -> "KeysendPlugin";
    }

    @Override
    public void onSetup(Setup setup) {

    }

    @Override
    public void onKit(Kit kit) {
        this.keySendActor = kit.system().actorOf(KeysendActor.props());
        this.kit = kit;
    }

    @Override
    public Function1<RequestContext, Future<RouteResult>> route(EclairDirectives directives) {
        final var send = new PaymentInitiator.SendSpontaneousPayment(
                new MilliSatoshi(690000L),
                new Crypto.PublicKey(PublicKey.fromHex("037e9d070515c8ba32e6a32fa698e568fc4944a1bb67ae2048947511267202509b")),
                ByteVector32.One(),
                2,
                Option.apply(""),
                kit.nodeParams().routerConf().pathFindingExperimentConf().getRandomConf().getDefaultRouteParams(),
                null,
                false
        );
        final var fut = ask(kit.paymentInitiator(), send, 1000L);
        final var s = FutureConverters.toJava(fut);

        return path("keysend", () ->
                onComplete(s, res -> complete((String) res.get()))
        ).asScala();
    }
}
