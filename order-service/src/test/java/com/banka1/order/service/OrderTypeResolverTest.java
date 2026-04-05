package com.banka1.order.service;

import com.banka1.order.entity.enums.OrderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for order type determination logic.
 */
class OrderTypeResolverTest {

    /**
     * Helper to determine order type.
     */
    private OrderType determineOrderType(BigDecimal limitValue, BigDecimal stopValue) {
        if (limitValue == null && stopValue == null) {
            return OrderType.MARKET;
        } else if (limitValue != null && stopValue == null) {
            return OrderType.LIMIT;
        } else if (limitValue == null && stopValue != null) {
            return OrderType.STOP;
        } else {
            return OrderType.STOP_LIMIT;
        }
    }

    @Test
    void onlyQuantity_returnsMarket() {
        OrderType type = determineOrderType(null, null);
        assertThat(type).isEqualTo(OrderType.MARKET);
    }

    @Test
    void quantityAndLimit_returnsLimit() {
        OrderType type = determineOrderType(new BigDecimal("100.00"), null);
        assertThat(type).isEqualTo(OrderType.LIMIT);
    }

    @Test
    void quantityAndStop_returnsStop() {
        OrderType type = determineOrderType(null, new BigDecimal("90.00"));
        assertThat(type).isEqualTo(OrderType.STOP);
    }

    @Test
    void quantityAndBoth_returnsStopLimit() {
        OrderType type = determineOrderType(new BigDecimal("100.00"), new BigDecimal("90.00"));
        assertThat(type).isEqualTo(OrderType.STOP_LIMIT);
    }

    @Test
    void limitOnly_isNotMarket() {
        OrderType type = determineOrderType(new BigDecimal("100.00"), null);
        assertThat(type).isNotEqualTo(OrderType.MARKET);
    }

    @Test
    void stopOnly_isNotMarket() {
        OrderType type = determineOrderType(null, new BigDecimal("90.00"));
        assertThat(type).isNotEqualTo(OrderType.MARKET);
    }
}

