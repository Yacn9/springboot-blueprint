package com.acme.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
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
        // The topic only exists once the event is published: look for it again every second, not every five minutes.
        earliest.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, "1000");

        try (Consumer<?, ?> consumer = consumers.createConsumer("externalization-test", null, null, earliest)) {
            consumer.subscribe(List.of(ExternalizationProbe.class.getName()));
            transactions.executeWithoutResult(status -> events.publishEvent(new ExternalizationProbe(marker)));

            List<String> received = new ArrayList<>();
            await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> received.add(text(record.value())));
                assertThat(received).anyMatch(value -> value.contains(marker));
            });
        }
    }

    /** Spring Modulith sends JSON as bytes (ByteArraySerializer), so values arrive as byte[], not text. */
    private static String text(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value);
    }
}

/** An event of the project's own, so the test does not depend on the sample modules. */
record ExternalizationProbe(String marker) {
}
