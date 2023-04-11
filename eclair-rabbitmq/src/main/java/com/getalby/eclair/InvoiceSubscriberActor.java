package com.getalby.eclair;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import fr.acinq.eclair.payment.PaymentEvent;
import fr.acinq.eclair.payment.PaymentReceived;

public class InvoiceSubscriberActor extends AbstractActor {

    private final LoggingAdapter logger = Logging.getLogger(getContext().getSystem(), this);
    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private final Channel channel;

    private InvoiceSubscriberActor(Channel channel) {
        this.channel = channel;
    }

    public static Props props(final Channel channel) {
        return Props.create(InvoiceSubscriberActor.class, channel);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(event -> {
                    if (event instanceof PaymentReceived) {
                        final PaymentReceived payment = (PaymentReceived) event;

                        final JsonObject payload = new JsonObject();
                        payload.addProperty("r_hash", payment.paymentHash().bytes().toBase64());
                        payload.addProperty("amt_paid_sat", payment.amount().toLong() / 1000L);
                        payload.addProperty("settled", true);
                        payload.addProperty("is_key_send", false);
                        payload.addProperty("state", 1);
                        payload.addProperty("settle_date", payment.timestamp().toLong());

                        logger.info(payload.toString());

                        try {
                            this.channel.basicPublish(
                                    "lnd_invoice",
                                    "invoice.incoming.settled",
                                    new AMQP.BasicProperties.Builder()
                                            .contentType("application/json")
                                            .build(),
                                    gson.toJson(payload).getBytes()
                            );
                        } catch (Exception e) {
                            logger.error(e.getMessage());
                        }
                    }
                })
                .build();
    }

    @Override
    public void preStart() {
        getContext()
                .getSystem()
                .eventStream()
                .subscribe(getSelf(), PaymentEvent.class);
    }

    @Override
    public void postStop() {
        getContext()
                .getSystem()
                .eventStream()
                .unsubscribe(getSelf());
    }
}
