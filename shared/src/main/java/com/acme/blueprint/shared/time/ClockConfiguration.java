package com.acme.blueprint.shared.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application code asks for the time through an injected {@link Clock}, never
 * {@code Instant.now()}, so tests can pin it.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
