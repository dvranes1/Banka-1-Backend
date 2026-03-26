package com.banka1.card_service.rest_client;

import java.math.BigDecimal;

/**
 * Internal account-service response used to resolve card-request ownership/type checks.
 *
 * @param accountNumber linked account number
 * @param ownerId client ID of the account owner
 * @param currency account currency code
 * @param availableBalance currently available balance
 * @param status account status
 * @param accountType ownership type string such as PERSONAL or BUSINESS
 */
public record InternalAccountDetailsDto(
        String accountNumber,
        Long ownerId,
        String currency,
        BigDecimal availableBalance,
        String status,
        String accountType
) {
}
