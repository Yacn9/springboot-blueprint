package com.acme.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.acme.blueprint.shared.testing.DatabaseTestcontainers;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** An event published in a transaction arrives on the events exchange, routed by its type, as JSON. */
@SpringBootTest
@Import(DatabaseTestcontainers.class)
class EventExternalizationTests {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    AmqpAdmin admin;

    @Autowired
    RabbitTemplate rabbit;

    @Test
    void publishesTheProjectsEventsToTheBroker() {
        String marker = UUID.randomUUID().toString();
        Queue queue = admin.declareQueue();
        admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(EventExternalization.EXCHANGE))
                .with(ExternalizationProbe.class.getName()));

        transactions.executeWithoutResult(status -> events.publishEvent(new ExternalizationProbe(marker)));

        Message received = rabbit.receive(queue.getName(), 60_000);
        assertThat(received).isNotNull();
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).contains(marker);
    }
}

/** An event of the project's own, so the test does not depend on the sample modules. */
record ExternalizationProbe(String marker) {
}
