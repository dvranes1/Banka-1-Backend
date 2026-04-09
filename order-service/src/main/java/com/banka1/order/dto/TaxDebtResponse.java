package com.banka1.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Data Transfer Object representing a user's capital gains tax debt.
 *
 * Aggregates the total tax obligation for a user across all trading activity.
 * Used in supervisor-facing endpoints to track tax liabilities.
 *
 * All amounts are expressed in RSD (Serbian Dinar). Foreign currency profits
 * are converted to RSD using the exchange-service.
 *
 * API Endpoints:
 * <ul>
 *   <li>GET /api/tax/debts - List all users' tax debts (paginated)</li>
 *   <li>GET /api/tax/debts/{userId} - Get specific user's tax debt</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaxDebtResponse {

    /**
     * Unique identifier of the user (client or agent).
     */
    private Long userId;

    /**
     * Total outstanding tax debt in RSD.
     * Includes:
     * <ul>
     *   <li>Tax from previous months not yet paid</li>
     *   <li>Tax accrued in current month</li>
     *   <li>Any penalties or adjustments (if applicable)</li>
     * </ul>
     */
    private BigDecimal debtRsd;
}