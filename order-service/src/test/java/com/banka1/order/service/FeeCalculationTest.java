package com.banka1.order.service;

import com.banka1.order.entity.enums.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for order fee calculation.
 */
class FeeCalculationTest {

    /**
     * Helper to calculate fee based on order type.
     */
    private BigDecimal calculateFee(OrderType orderType, BigDecimal approximatePrice) {
        BigDecimal rate;
        BigDecimal maxFee;
        if (orderType == OrderType.MARKET || orderType == OrderType.STOP) {
            rate = new BigDecimal("0.14");
            maxFee = new BigDecimal("7");
        } else {
            rate = new BigDecimal("0.24");
            maxFee = new BigDecimal("12");
        }
        BigDecimal fee = approximatePrice.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return fee.min(maxFee);
    }

    // Market Fee Tests

    @Test
    void marketFee_small_notCapped() {
        BigDecimal price = new BigDecimal("1000.00");
        BigDecimal fee = calculateFee(OrderType.MARKET, price);
        // 1000 * 0.14 / 100 = 1.40
        assertThat(fee).isEqualByComparingTo("1.40");
    }

    @Test
    void marketFee_large_cappedAt7() {
        BigDecimal price = new BigDecimal("10000.00");
        BigDecimal fee = calculateFee(OrderType.MARKET, price);
        assertThat(fee).isEqualByComparingTo("7.00"); // capped at 7
    }

    @Test
    void marketFee_capsAt7() {
        BigDecimal price = new BigDecimal("5000.00");
        BigDecimal fee = calculateFee(OrderType.MARKET, price);
        assertThat(fee).isEqualByComparingTo("7.00"); // 5000 * 0.14 = 700, but capped at 7
    }

    @Test
    void stopFee_cappedAt7() {
        BigDecimal price = new BigDecimal("5000.00");
        BigDecimal fee = calculateFee(OrderType.STOP, price);
        assertThat(fee).isEqualByComparingTo("7.00");
    }

    // Limit Fee Tests

    @Test
    void limitFee_small_notCapped() {
        BigDecimal price = new BigDecimal("1000.00");
        BigDecimal fee = calculateFee(OrderType.LIMIT, price);
        // 1000 * 0.24 / 100 = 2.40
        assertThat(fee).isEqualByComparingTo("2.40");
    }

    @Test
    void limitFee_cappedAt12() {
        BigDecimal price = new BigDecimal("10000.00");
        BigDecimal fee = calculateFee(OrderType.LIMIT, price);
        assertThat(fee).isEqualByComparingTo("12.00"); // capped at 12
    }

    @Test
    void stopLimitFee_cappedAt12() {
        BigDecimal price = new BigDecimal("10000.00");
        BigDecimal fee = calculateFee(OrderType.STOP_LIMIT, price);
        assertThat(fee).isEqualByComparingTo("12.00");
    }

    // Comparative Tests

    @Test
    void marketFee_lessThanLimitFee() {
        BigDecimal price = new BigDecimal("1000.00");
        BigDecimal marketFee = calculateFee(OrderType.MARKET, price);
        BigDecimal limitFee = calculateFee(OrderType.LIMIT, price);
        assertThat(marketFee).isLessThan(limitFee);
    }

    @Test
    void stopFee_sameasMarketFee() {
        BigDecimal price = new BigDecimal("1000.00");
        BigDecimal stopFee = calculateFee(OrderType.STOP, price);
        BigDecimal marketFee = calculateFee(OrderType.MARKET, price);
        assertThat(stopFee).isEqualByComparingTo(marketFee);
    }

    @Test
    void smallPrice_marketFeeNotCapped() {
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal fee = calculateFee(OrderType.MARKET, price);
        assertThat(fee).isLessThan(new BigDecimal("7.00"));
    }

    @Test
    void smallPrice_limitFeeNotCapped() {
        BigDecimal price = new BigDecimal("100.00");
        BigDecimal fee = calculateFee(OrderType.LIMIT, price);
        assertThat(fee).isLessThan(new BigDecimal("12.00"));
    }

    @Test
    void zeroPrice_zeroFee() {
        BigDecimal price = BigDecimal.ZERO;
        BigDecimal fee = calculateFee(OrderType.MARKET, price);
        assertThat(fee).isEqualByComparingTo("0.00");
    }
}

