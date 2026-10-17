package com.acme.blueprint.shared.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on Spring's cache annotations for every module. What holds the
 * entries is configuration — {@code spring.cache} in the platform defaults —
 * so a module's code is the same whichever cache the project runs with.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfiguration {
}
