package com.acme.blueprint.shared.config;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Contributes {@code config/blueprint-platform.yml} — the settings every module
 * runs with — to every Spring application on the classpath: the deployable,
 * each module's isolated tests and test slices alike.
 *
 * <p>It runs after Spring Boot has loaded {@code application.yml} and adds the
 * defaults with the <em>lowest</em> precedence, so any {@code application.yml},
 * profile, environment variable or test property can override a platform
 * value — and no module can lose the defaults by adding a config file of its
 * own. Registered in {@code META-INF/spring.factories}.
 */
class PlatformDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String LOCATION = "config/blueprint-platform.yml";
    private static final String SOURCE_NAME = "blueprintPlatformDefaults";
    private static final String SPRING_DEFAULT_PROPERTIES = "defaultProperties";

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(SOURCE_NAME)) {
            return;
        }
        for (PropertySource<?> defaults : load()) {
            if (sources.contains(SPRING_DEFAULT_PROPERTIES)) {
                sources.addBefore(SPRING_DEFAULT_PROPERTIES, defaults);
            } else {
                sources.addLast(defaults);
            }
        }
    }

    /** After application.yml has been loaded, so "last" really means below it. */
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private Iterable<PropertySource<?>> load() {
        try {
            return loader.load(SOURCE_NAME, new ClassPathResource(LOCATION));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + LOCATION, exception);
        }
    }
}
