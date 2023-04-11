package com.getalby.eclair;

import akka.actor.AbstractActor;
import akka.actor.Props;

public class KeysendActor extends AbstractActor {

    public static Props props() {
        return Props.create(KeysendActor.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(event -> System.out.println(event.getClass()))
                .build();
    }
}
