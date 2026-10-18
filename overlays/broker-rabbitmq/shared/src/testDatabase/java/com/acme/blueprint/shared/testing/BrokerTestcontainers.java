package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * The message broker the integration tests run against, imported with
 * {@link DatabaseTestcontainers}: RabbitMQ, which Spring Boot connects to
 * ({@link ServiceConnection}). One container for every test context in this
 * JVM. Keep the image in step with compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers
public class BrokerTestcontainers {

    @ServiceConnection
    static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:4-management-alpine");
}
