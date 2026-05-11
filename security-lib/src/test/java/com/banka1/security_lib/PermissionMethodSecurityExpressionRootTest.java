package com.banka1.security_lib;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionMethodSecurityExpressionRootTest {

    @Test
    void hasPermissionReturnsTrueWhenJwtClaimContainsRequestedPermission() {
        SecurityProperties properties = new SecurityProperties();
        properties.setPermissionsClaim("permissions");
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("permissions", List.of("OTC_TRADE", "FUND_AGENT_MANAGE")));

        PermissionMethodSecurityExpressionRoot root = new PermissionMethodSecurityExpressionRoot(
                new JwtAuthenticationToken(jwt),
                properties);

        assertThat(root.hasPermission("OTC_TRADE")).isTrue();
        assertThat(root.hasPermission("MARGIN_TRADE")).isFalse();
    }
}
