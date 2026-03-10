package com.company.observability.starter.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationUserIdExtractTest {

    private final JwtAuthenticationUserIdExtractor extractor = new JwtAuthenticationUserIdExtractor();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExtractUserIdFromMapPrincipal() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(Map.of("userId", "42"));
        when(authentication.getDetails()).thenReturn(null);
        when(authentication.getName()).thenReturn("ignored");

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<String> result = extractor.extractUserId();

        assertTrue(result.isPresent());
        assertEquals("42", result.get());
    }

    @Test
    void shouldExtractUserIdViaReflectionClaimMethod() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new ClaimPrincipal());
        when(authentication.getDetails()).thenReturn(null);
        when(authentication.getName()).thenReturn("ignored");

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<String> result = extractor.extractUserId();

        assertTrue(result.isPresent());
        assertEquals("99", result.get());
    }

    @Test
    void shouldReturnEmptyWhenUnauthenticated() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<String> result = extractor.extractUserId();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForAnonymousAuthenticationToken() {
        AnonymousAuthenticationToken authentication = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<String> result = extractor.extractUserId();

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFallbackToAuthenticationName() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(new Object());
        when(authentication.getDetails()).thenReturn(null);
        when(authentication.getName()).thenReturn("fallback-user");

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<String> result = extractor.extractUserId();

        assertTrue(result.isPresent());
        assertEquals("fallback-user", result.get());
    }

    static class ClaimPrincipal {
        public String getClaimAsString(String claimName) {
            if ("userId".equals(claimName)) {
                return "99";
            }
            return null;
        }
    }
}