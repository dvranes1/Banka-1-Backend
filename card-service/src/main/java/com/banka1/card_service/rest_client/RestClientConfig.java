package com.banka1.card_service.rest_client;

import com.banka1.card_service.security.JWTService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP client configuration for internal service-to-service calls.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient clientServiceClient(
            RestClient.Builder builder,
            @Value("${services.client.url}") String baseUrl,
            JWTService jwtService
    ) {
        return builder
                .baseUrl(baseUrl)
                .requestInterceptor(new JwtAuthInterceptor(jwtService))
                .build();
    }

    @Bean
    public RestClient accountServiceClient(
            RestClient.Builder builder,
            @Value("${services.account.url}") String baseUrl,
            JWTService jwtService
    ) {
        return builder
                .baseUrl(baseUrl)
                .requestInterceptor(new JwtAuthInterceptor(jwtService))
                .build();
    }
}
