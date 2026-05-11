package com.banka1.employeeService.client.impl;

import com.banka1.employeeService.client.InvestmentFundClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * RestClient-based implementation of {@link InvestmentFundClient}.
 */
@Component
@RequiredArgsConstructor
public class InvestmentFundClientImpl implements InvestmentFundClient {

    @Qualifier("investmentFundRestClient")
    private final RestClient investmentFundRestClient;

    @Override
    public void transferManagement(Long fromUserId, Long toUserId, String bearerToken) {
        investmentFundRestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/funds/transfer-management")
                        .queryParam("fromUserId", fromUserId)
                        .queryParam("toUserId", toUserId)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .toBodilessEntity();
    }
}
