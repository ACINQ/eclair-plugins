package com.getalby.eclair;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import fr.acinq.eclair.Kit;
import fr.acinq.eclair.Plugin;
import fr.acinq.eclair.PluginParams;
import fr.acinq.eclair.Setup;

public class RabbitMQPlugin implements Plugin {

    private Channel channel;

    @Override
    public PluginParams params() {
        return () -> "RabbitMQPlugin";
    }

    @Override
    public void onSetup(Setup setup) {
        final ConnectionFactory factory = new ConnectionFactory();

        factory.setUsername(setup.config().getString("rabbitmq.username"));
        factory.setPassword(setup.config().getString("rabbitmq.password"));
        factory.setHost(setup.config().getString("rabbitmq.host"));
        factory.setPort(setup.config().getInt("rabbitmq.port"));

        try {
            final Connection conn = factory.newConnection();
            this.channel = conn.createChannel();

        } catch (Exception e) {
            setup.logger().logger().error("RabbitMQ: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void onKit(Kit kit) {
        kit.system().actorOf(InvoiceSubscriberActor.props(this.channel));
    }
}
