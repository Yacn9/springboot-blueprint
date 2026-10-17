package com.acme.blueprint;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;

/**
 * The modules' events reach the message broker too. Spring Modulith publishes
 * each one after its transaction commits, through the same publication
 * registry as the in-process listeners — so a broker that is down delays
 * delivery instead of losing it. Each event goes to a topic named after its
 * type, such as {@code com.acme.blueprint.users.UserRegistered}.
 *
 * <p>It lives in the composition root: modules publish plain events and know
 * nothing about the broker.
 */
@Configuration(proxyBeanMethods = false)
class EventExternalization {

    @Bean
    EventExternalizationConfiguration eventExternalization() {
        return EventExternalizationConfiguration.externalizing()
                .selectByPackage("com.acme.blueprint")
                .build();
    }
}
