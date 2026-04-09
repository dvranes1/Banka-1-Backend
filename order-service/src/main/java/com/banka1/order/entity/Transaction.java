package com.banka1.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records a single executed portion of an order.
 *
 * When an order is executed, it may be filled incrementally (partial fills) or
 * all at once. Each partial execution is recorded as a separate Transaction entity.
 *
 * For example:
 * <ul>
 *   <li>Market orders: typically 1 transaction per order</li>
 *   <li>Limit orders: may have multiple transactions as price conditions are met</li>
 *   <li>All-or-None orders: exactly 1 transaction (full fill) or none</li>
 * </ul>
 *
 * Transactions are used for:
 * <ul>
 *   <li>Portfolio position updates</li>
 *   <li>Tax calculation (matched with cost basis)</li>
 *   <li>Commission and fee tracking</li>
 *   <li>Audit trail of executed trades</li>
 * </ul>
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    /** Unique transaction identifier. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to the parent order this transaction fulfills. */
    @Column(nullable = false)
    private Long orderId;

    /** Number of units filled in this execution portion. */
    @Column(nullable = false)
    private Integer quantity;

    /** Execution price per unit at the time this portion was filled. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal pricePerUnit;

    /** Total value of this execution: {@code quantity * pricePerUnit}. In the security's native currency. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPrice;

    /** Commission/fee charged for this execution. Deducted from settlement amount and transferred to bank. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal commission;

    /** Timestamp when this execution was recorded. Set automatically on creation. */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * JPA callback that sets the execution timestamp to the current time.
     * Called before the first persist operation if timestamp is not already assigned.
     */
    @PrePersist
    public void setTimestamp() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
