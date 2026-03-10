package com.company.observability.starter.bootstrap;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link org.springframework.boot.EnvironmentPostProcessor} implementacija koja
 * obezbedjuje podrazumevanu konfiguraciju za logovanje observability starter biblioteke.
 * <p>
 * Ako aplikacija vec nema definisanu vrednost za svojstvo {@code logging.config},
 * postavlja se podrazumevana Logback konfiguracija iz biblioteke.
 * Ako je svojstvo vec definisano od strane aplikacije, postojeca vrednost se ne menja.
 * <p>
 * Ova klasa se izvrsava veoma rano tokom bootstrap faze aplikacije.
 */
public class ObservabilityEnvPostProcessor implements EnvironmentPostProcessor, Ordered {
    private static final String PROPERTY_SOURCE_NAME = "ObservabilityPropertySource";
    private static final String LOGGING_CONFIG_KEY = "logging.config";
    private static final String DEFAULT_LOGGING_CONFIG = "classpath:company-observability-starter/logback-spring.xml";

    /**
     * Postavlja podrazumevanu vrednost za {@code logging.config} ako aplikacija
     * vec nije definisala sopstvenu logging konfiguraciju.
     *
     * @param environment Spring okruzenje aplikacije
     * @param application trenutna Spring aplikacija
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String existingLoggingConfig = environment.getProperty(LOGGING_CONFIG_KEY);
        if(existingLoggingConfig != null && !existingLoggingConfig.isBlank()) {
            // Ako aplikacija već ima definisan logging.config, ne menjamo ga
            return;
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put(LOGGING_CONFIG_KEY, DEFAULT_LOGGING_CONFIG);
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    /**
     * Vraca prioritet izvrsavanja ovog post-processora.
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE}
     */
    @Override
    public int getOrder(){
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
