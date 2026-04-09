package com.banka1.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request DTO for creating a sell order.
 *
 * Defines the parameters required to place a new sell order for securities.
 * Supports market and conditional orders (limit, stop, stop-limit).
 * Validates that all required fields are positive and present.
 */
@Data
public class CreateSellOrderRequest {
    /** ID of the security listing to sell. Must be positive and valid in stock-service. */
    @NotNull
    @Positive
    private Long listingId;

    /** Number of securities to sell. Must be positive. Validated against portfolio quantity. */
    @NotNull
    @Positive
    private Integer quantity;

    /** Limit price for LIMIT and STOP_LIMIT orders. Required for those types, ignored for MARKET and STOP. */
    @Positive
    private BigDecimal limitValue;

    /** Stop price for STOP and STOP_LIMIT orders. Required for those types, ignored for MARKET and LIMIT. */
    @Positive
    private BigDecimal stopValue;

    /** Whether the order must be filled completely or not at all. Defaults to false (allows partial fills). */
    private Boolean allOrNone = false;

    /** Whether to use margin for this order. Defaults to false. For sell orders, margin usage may have special implications. */
    private Boolean margin = false;

    /** ID of the account to credit funds to. Must be positive and belong to the authenticated user. */
    @NotNull
    @Positive
    private Long accountId;
}
