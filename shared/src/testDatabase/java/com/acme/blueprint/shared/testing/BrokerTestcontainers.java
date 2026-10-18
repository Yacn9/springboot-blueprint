package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * The message broker the integration tests run against, imported with
 * {@link DatabaseTestcontainers}. None here: this project keeps its events
 * inside the application.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BrokerTestcontainers {
}
