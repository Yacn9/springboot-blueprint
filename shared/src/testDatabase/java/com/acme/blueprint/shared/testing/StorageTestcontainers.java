package com.acme.blueprint.shared.testing;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * The object storage the integration tests run against, imported with
 * {@link DatabaseTestcontainers}. None here: this project keeps no files
 * outside its database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StorageTestcontainers {
}
