package com.banka1.stock_service.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;

import static org.assertj.core.api.Assertions.assertThat;

class StockServiceResponseStatusExceptionHandlerTest {

    private final StockServiceResponseStatusExceptionHandler handler = new StockServiceResponseStatusExceptionHandler();

    @Test
    void authorizationDeniedExceptionIsMappedTo403() {
        // GHI #199 follow-up: Spring Security 6.x baca AuthorizationDeniedException pri PreAuthorize
        // neuspehu; bez ovog handlera generic Exception fallback bi vratio 500 i frontend bi pokazao
        // "Serverska greska" umesto cistog 403 kao na refresh-all kada je pozivalac AGENT/BASIC.
        HttpServletRequest request = new MockHttpServletRequest("POST", "/admin/stocks/refresh-all");

        ResponseEntity<?> response = handler.handleAuthorizationDenied(
                new AuthorizationDeniedException("denied", new AuthorizationDecision(false)),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void accessDeniedExceptionIsAlsoMappedTo403() {
        // Stara klasa iz spring-security-access; obe rute treba da vrate 403, ne 500.
        HttpServletRequest request = new MockHttpServletRequest("POST", "/admin/stocks/refresh-all");

        ResponseEntity<?> response = handler.handleAuthorizationDenied(
                new AccessDeniedException("denied"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
