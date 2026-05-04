package com.banka1.order.dto.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for the new account-service one-sided endpoints
 * ({@code POST /internal/accounts/exchange/buy} and {@code .../exchange/sell}).
 *
 * <p>Per the issue #199 directive, exchange settlement legs must not credit
 * (or debit) the bank's own account; only the trader's account moves. This DTO
 * carries just enough information for that adjustment. Field names mirror the
 * server-side {@code com.banka1.account_service.dto.request.OneSidedTransactionDto}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OneSidedTransactionDto {
    /** 19-digit account number whose balance will be adjusted. */
    private String accountNumber;
    /** Positive amount in the account's own currency. */
    private BigDecimal amount;
    /** Owner of the account, used for audit logging on the account-service side. */
    private Long clientId;
    /** Free-form description of the operation for audit. */
    private String description;
}
