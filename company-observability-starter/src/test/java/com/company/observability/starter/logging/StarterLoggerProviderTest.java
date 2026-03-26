package com.company.observability.starter.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StarterLoggerProviderTest {

    @Test
    void providerReturnsLogger() {
        StarterLoggerProvider provider = new StarterLoggerProvider();
        var logger = provider.getLogger(StarterLoggerProviderTest.class);
        assertNotNull(logger);
        // should be able to call methods without throwing
        logger.info("Test log from StarterLoggerProviderTest");
    }
}


