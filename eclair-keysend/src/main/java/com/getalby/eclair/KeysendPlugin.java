package com.getalby.eclair;

import static akka.http.javadsl.server.Directives.complete;

import akka.http.scaladsl.server.RequestContext;
import akka.http.scaladsl.server.RouteResult;
import fr.acinq.eclair.*;
import fr.acinq.eclair.api.directives.EclairDirectives;
import scala.Function1;

import scala.concurrent.Future;


public class KeysendPlugin implements Plugin, RouteProvider {

    @Override
    public PluginParams params() {
        return () -> "KeysendPlugin";
    }

    @Override
    public void onSetup(Setup setup) {

    }

    @Override
    public void onKit(Kit kit) {
    }

    @Override
    public Function1<RequestContext, Future<RouteResult>> route(EclairDirectives directives) {
        return directives.postRequest("keysend").tapply(t -> {
            return complete("Hello").asScala();
        });
    }
}
