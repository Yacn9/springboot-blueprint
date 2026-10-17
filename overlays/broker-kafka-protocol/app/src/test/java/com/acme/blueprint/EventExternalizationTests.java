package com.acme.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import com.acme.blueprint.shared.testing.DatabaseTestcontainers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.transaction.support.TransactionTemplate;

/** An event published in a transaction arrives on the broker, under its type's name, as JSON. */
@SpringBootTest
@Import(DatabaseTestcontainers.class)
class EventExternalizationTests {

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    ConsumerFactory<?, ?> consumers;

    @Test
    void publishesTheProjectsEventsToTheBroker() {
        String marker = UUID.randomUUID().toString();
        Properties earliest = new Properties();
        earliest.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<?, ?> consumer = consumers.createConsumer("externalization-test", null, null, earliest)) {
            consumer.subscribe(List.of(ExternalizationProbe.class.getName()));
            transactions.executeWithoutResult(status -> events.publishEvent(new ExternalizationProbe(marker)));

            List<String> received = new ArrayList<>();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> received.add(String.valueOf(record.value())));
                assertThat(received).anyMatch(value -> value.contains(marker));
            });
        }
    }
}

/** An event of the project's own, so the test does not depend on the sample modules. */
record ExternalizationProbe(String marker) {
}
