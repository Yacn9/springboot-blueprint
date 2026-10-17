package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * The cache server the integration tests run against, imported with
 * {@link DatabaseTestcontainers}. None here: this project's cache, if any,
 * lives in the application's memory.
 */
@TestConfiguration(proxyBeanMethods = false)
public class CacheTestcontainers {
}
