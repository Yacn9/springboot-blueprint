package com.acme.blueprint.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PlatformDefaultsEnvironmentPostProcessorTest {

    private final StandardEnvironment environment = new StandardEnvironment();
    private final PlatformDefaultsEnvironmentPostProcessor postProcessor = new PlatformDefaultsEnvironmentPostProcessor();

    @Test
    void contributesThePlatformDefaults() {
        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.modulith.events.completion-mode")).isEqualTo("delete");
    }

    @Test
    void letsApplicationConfigurationOverrideADefault() {
        environment.getPropertySources().addLast(new MapPropertySource(
                "application.yml", Map.of("spring.modulith.events.completion-mode", "archive")));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.modulith.events.completion-mode")).isEqualTo("archive");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }
}
