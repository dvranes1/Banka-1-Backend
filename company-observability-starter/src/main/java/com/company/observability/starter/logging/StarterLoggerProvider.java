package com.company.observability.starter.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple provider that delegates to SLF4J LoggerFactory. Registered as a
 * Spring bean so consumers can inject it when they prefer instance-based
 * logger creation instead of static LoggerFactory calls.
 */
public final class StarterLoggerProvider {

    /**
     * Obtain a logger for the given class.
     */
    public Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * Obtain a logger for the given name.
     */
    public Logger getLogger(String name) {
        return LoggerFactory.getLogger(name);
    }
}

