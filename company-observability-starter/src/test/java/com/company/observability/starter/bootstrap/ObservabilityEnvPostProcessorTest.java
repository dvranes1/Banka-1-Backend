package com.company.observability.starter.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilityEnvPostProcessorTest {

    private static final String LOGGING_CONFIG_KEY = "logging.config";
    private static final String DEFAULT_LOGGING_CONFIG =
            "classpath:company-observability-starter/logback-spring.xml";

    private final ObservabilityEnvPostProcessor postProcessor = new ObservabilityEnvPostProcessor();

    @Test
    void shouldSetDefaultLoggingConfigWhenMissing() {
        ConfigurableEnvironment environment = new MockEnvironment();
        SpringApplication application = new SpringApplication(Object.class);

        postProcessor.postProcessEnvironment(environment, application);

        assertEquals(DEFAULT_LOGGING_CONFIG, environment.getProperty(LOGGING_CONFIG_KEY));

        assertNotNull(environment.getPropertySources().get("ObservabilityPropertySource"));
        assertEquals(
                DEFAULT_LOGGING_CONFIG,
                environment.getPropertySources()
                        .get("ObservabilityPropertySource")
                        .getProperty(LOGGING_CONFIG_KEY)
        );
    }

    @Test
    void shouldNotOverrideExistingLoggingConfig() {
        ConfigurableEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource(
                        "existingProperties",
                        Map.of(LOGGING_CONFIG_KEY, "classpath:custom-logback.xml")
                )
        );

        SpringApplication application = new SpringApplication(Object.class);

        postProcessor.postProcessEnvironment(environment, application);

        assertEquals("classpath:custom-logback.xml", environment.getProperty(LOGGING_CONFIG_KEY));
        assertNull(environment.getPropertySources().get("ObservabilityPropertySource"));
    }

    @Test
    void shouldNotOverrideBlankAwareWhenPropertyAlreadyHasText() {
        ConfigurableEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource(
                        "existingProperties",
                        Map.of(LOGGING_CONFIG_KEY, "file:/etc/app/logback.xml")
                )
        );

        SpringApplication application = new SpringApplication(Object.class);

        postProcessor.postProcessEnvironment(environment, application);

        assertEquals("file:/etc/app/logback.xml", environment.getProperty(LOGGING_CONFIG_KEY));
        assertNull(environment.getPropertySources().get("ObservabilityPropertySource"));
    }

    @Test
    void shouldSetOrderToHighestPrecedence() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, postProcessor.getOrder());
    }
}