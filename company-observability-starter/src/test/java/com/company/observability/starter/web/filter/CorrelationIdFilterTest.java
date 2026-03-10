package com.company.observability.starter.web.filter;

import com.company.observability.starter.config.ObservabilityProperties;
import com.company.observability.starter.domain.CorrelationIdGenerator;
import com.company.observability.starter.service.CorrelationIdService;
import com.company.observability.starter.service.UserIdMdcService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelationIdFilterTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldGenerateCorrelationIdSetResponseHeaderAndRestoreEmptyMdc() throws Exception {
        CorrelationIdGenerator generator = mock(CorrelationIdGenerator.class);
        when(generator.generate()).thenReturn("generated-id");

        CorrelationIdService correlationIdService = new CorrelationIdService(generator);
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setCorrelationHeaderName("X-Correlation-Id");

        UserIdMdcService userIdMdcService = mock(UserIdMdcService.class);

        CorrelationIdFilter filter = new CorrelationIdFilter(
                correlationIdService,
                properties,
                userIdMdcService
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) ->
                assertEquals("generated-id", MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertEquals("generated-id", response.getHeader("X-Correlation-Id"));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
        verify(userIdMdcService).putUserIdIfPresent();
        verify(generator).generate();
    }

    @Test
    void shouldPropagateExistingCorrelationId() throws Exception {
        CorrelationIdGenerator generator = mock(CorrelationIdGenerator.class);
        CorrelationIdService correlationIdService = new CorrelationIdService(generator);

        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setCorrelationHeaderName("X-Correlation-Id");

        UserIdMdcService userIdMdcService = mock(UserIdMdcService.class);

        CorrelationIdFilter filter = new CorrelationIdFilter(
                correlationIdService,
                properties,
                userIdMdcService
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo/ping");
        request.addHeader("X-Correlation-Id", "existing-id");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) ->
                assertEquals("existing-id", MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertEquals("existing-id", response.getHeader("X-Correlation-Id"));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
        verify(userIdMdcService).putUserIdIfPresent();
        verifyNoInteractions(generator);
    }

    @Test
    void shouldRestorePreviousMdcContextAfterRequest() throws Exception {
        CorrelationIdGenerator generator = mock(CorrelationIdGenerator.class);
        when(generator.generate()).thenReturn("generated-id");

        CorrelationIdService correlationIdService = new CorrelationIdService(generator);
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.setCorrelationHeaderName("X-Correlation-Id");

        UserIdMdcService userIdMdcService = mock(UserIdMdcService.class);

        CorrelationIdFilter filter = new CorrelationIdFilter(
                correlationIdService,
                properties,
                userIdMdcService
        );

        MDC.put("traceId", "trace-123");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/demo/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertEquals("trace-123", MDC.get("traceId"));
            assertEquals("generated-id", MDC.get(CorrelationIdFilter.MDC_KEY));
        };

        filter.doFilter(request, response, chain);

        assertEquals("trace-123", MDC.get("traceId"));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
        verify(userIdMdcService).putUserIdIfPresent();
        verify(generator).generate();
    }
}