package com.company.observability.starter.service;

import com.company.observability.starter.domain.CorrelationContext;
import com.company.observability.starter.domain.CorrelationIdGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CorrelationIdServiceTest {

    @Test
    void shouldReturnExistingCorrelationIdWhenProvided() {
        CorrelationIdGenerator generator = mock(CorrelationIdGenerator.class);
        CorrelationIdService service = new CorrelationIdService(generator);

        CorrelationContext result = service.resolve("existing-id");

        assertEquals("existing-id", result.correlationId());
        verifyNoInteractions(generator);
    }

    @Test
    void shouldGenerateCorrelationIdWhenMissing() {
        CorrelationIdGenerator generator = mock(CorrelationIdGenerator.class);
        when(generator.generate()).thenReturn("generated-id");

        CorrelationIdService service = new CorrelationIdService(generator);

        CorrelationContext result = service.resolve(null);

        assertEquals("generated-id", result.correlationId());
        verify(generator).generate();
    }

    @Test
    void shouldGenerateCorrelationIdWhenBlank() {
        CorrelationIdGenerator generator = mock(CorrelationIdGenerator.class);
        when(generator.generate()).thenReturn("generated-id");

        CorrelationIdService service = new CorrelationIdService(generator);

        CorrelationContext result = service.resolve("   ");

        assertEquals("generated-id", result.correlationId());
        verify(generator).generate();
    }

    @Test
    void shouldTrimIncomingCorrelationId() {
        CorrelationIdGenerator generator = mock(CorrelationIdGenerator.class);
        CorrelationIdService service = new CorrelationIdService(generator);

        CorrelationContext result = service.resolve("  test-id  ");

        assertEquals("test-id", result.correlationId());
        verifyNoInteractions(generator);
    }
}