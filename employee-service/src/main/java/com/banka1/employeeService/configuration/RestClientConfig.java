package com.banka1.employeeService.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP client configuration for downstream service integrations.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient investmentFundRestClient(@Value("${services.investment-fund.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
