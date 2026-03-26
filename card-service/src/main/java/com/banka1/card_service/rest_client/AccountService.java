package com.banka1.card_service.rest_client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Internal account-service adapter used to resolve card/account context
 * for notification routing and request validation.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final RestClient accountServiceClient;

    /**
     * Loads whether the linked account is business-owned and the owner client ID.
     *
     * <p>This assumes account-service exposes the internal route
     * {@code /internal/accounts/{accountNumber}/card-context}.
     *
     * @param accountNumber linked account number
     * @return account card context
     */
    public AccountNotificationContextDto getAccountContext(String accountNumber) {
        return accountServiceClient.get()
                .uri("/internal/accounts/{accountNumber}/card-context", accountNumber)
                .retrieve()
                .body(AccountNotificationContextDto.class);
    }

    /**
     * Backward-compatible alias used by lifecycle notification flows.
     *
     * @param accountNumber linked account number
     * @return account card context
     */
    public AccountNotificationContextDto getNotificationContext(String accountNumber) {
        return getAccountContext(accountNumber);
    }
}
