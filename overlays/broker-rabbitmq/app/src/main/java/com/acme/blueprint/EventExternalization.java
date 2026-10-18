package com.acme.blueprint;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/**
 * The modules' events reach RabbitMQ too. Spring Modulith publishes each one
 * after its transaction commits, through the same publication registry as the
 * in-process listeners — so a broker that is down delays delivery instead of
 * losing it. All go to one topic exchange, routed by type
 * ({@code com.acme.blueprint.users.UserRegistered}): bind a queue with
 * {@code #} for everything, or with a type for one kind of event.
 *
 * <p>It lives in the composition root: modules publish plain events and know
 * nothing about the broker.
 */
@Configuration(proxyBeanMethods = false)
class EventExternalization {

    static final String EXCHANGE = "blueprint.events";

    /** Declared on RabbitMQ when the application connects. */
    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    EventExternalizationConfiguration externalizedEvents() {
        return EventExternalizationConfiguration.externalizing()
                .selectByPackage("com.acme.blueprint")
                .routeAll(event -> RoutingTarget.forTarget(EXCHANGE).andKey(event.getClass().getName()))
                .build();
    }
}
