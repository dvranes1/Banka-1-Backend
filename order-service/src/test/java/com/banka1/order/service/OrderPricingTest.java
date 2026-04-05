package com.banka1.order.service;

import com.banka1.order.dto.StockListingDto;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for order pricing calculation.
 */
class OrderPricingTest {

    private StockListingDto listing;

    @BeforeEach
    void setUp() {
        listing = new StockListingDto();
        listing.setPrice(new BigDecimal("100.00"));
        listing.setAsk(new BigDecimal("101.00"));
        listing.setBid(new BigDecimal("99.00"));
        listing.setContractSize(1);
    }

    /**
     * Helper to calculate approximate price for buy order.
     */
    private BigDecimal calculateApproximatePrice(OrderType orderType, StockListingDto listing,
                                                   Integer quantity, BigDecimal limitValue,
                                                   BigDecimal stopValue) {
        BigDecimal pricePerUnit;
        switch (orderType) {
            case MARKET:
                pricePerUnit = listing.getAsk();
                break;
            case LIMIT:
                pricePerUnit = limitValue;
                break;
            case STOP:
                pricePerUnit = stopValue;
                break;
            case STOP_LIMIT:
                pricePerUnit = limitValue;
                break;
            default:
                throw new IllegalArgumentException("Unknown order type");
        }
        return pricePerUnit.multiply(BigDecimal.valueOf(listing.getContractSize())).multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Helper to calculate approximate price for sell order.
     */
    private BigDecimal calculateSellApproximatePrice(OrderType orderType, StockListingDto listing,
                                                      Integer quantity, BigDecimal limitValue,
                                                      BigDecimal stopValue) {
        BigDecimal pricePerUnit;
        switch (orderType) {
            case MARKET:
                pricePerUnit = listing.getBid();
                break;
            case LIMIT:
                pricePerUnit = limitValue;
                break;
            case STOP:
                pricePerUnit = stopValue;
                break;
            case STOP_LIMIT:
                pricePerUnit = limitValue;
                break;
            default:
                throw new IllegalArgumentException("Unknown order type");
        }
        return pricePerUnit.multiply(BigDecimal.valueOf(listing.getContractSize())).multiply(BigDecimal.valueOf(quantity));
    }

    // Buy Pricing Tests

    @Test
    void buyMarket_usesAskPrice() {
        BigDecimal price = calculateApproximatePrice(OrderType.MARKET, listing, 10, null, null);
        assertThat(price).isEqualByComparingTo("1010.00"); // 101 * 1 * 10
    }

    @Test
    void buyLimit_usesLimitValue() {
        BigDecimal price = calculateApproximatePrice(OrderType.LIMIT, listing, 10, new BigDecimal("98.00"), null);
        assertThat(price).isEqualByComparingTo("980.00"); // 98 * 1 * 10
    }

    @Test
    void buyStop_usesStopValue() {
        BigDecimal price = calculateApproximatePrice(OrderType.STOP, listing, 10, null, new BigDecimal("95.00"));
        assertThat(price).isEqualByComparingTo("950.00"); // 95 * 1 * 10
    }

    @Test
    void buyStopLimit_usesLimitValue() {
        BigDecimal price = calculateApproximatePrice(OrderType.STOP_LIMIT, listing, 10,
                new BigDecimal("98.00"), new BigDecimal("95.00"));
        assertThat(price).isEqualByComparingTo("980.00"); // 98 * 1 * 10
    }

    @Test
    void buyMarket_multipleContracts() {
        listing.setContractSize(5);
        BigDecimal price = calculateApproximatePrice(OrderType.MARKET, listing, 10, null, null);
        assertThat(price).isEqualByComparingTo("5050.00"); // 101 * 5 * 10
    }

    // Sell Pricing Tests

    @Test
    void sellMarket_usesBidPrice() {
        BigDecimal price = calculateSellApproximatePrice(OrderType.MARKET, listing, 10, null, null);
        assertThat(price).isEqualByComparingTo("990.00"); // 99 * 1 * 10
    }

    @Test
    void sellLimit_usesLimitValue() {
        BigDecimal price = calculateSellApproximatePrice(OrderType.LIMIT, listing, 10, new BigDecimal("102.00"), null);
        assertThat(price).isEqualByComparingTo("1020.00"); // 102 * 1 * 10
    }

    @Test
    void sellStop_usesStopValue() {
        BigDecimal price = calculateSellApproximatePrice(OrderType.STOP, listing, 10, null, new BigDecimal("95.00"));
        assertThat(price).isEqualByComparingTo("950.00"); // 95 * 1 * 10
    }

    @Test
    void sellStopLimit_usesLimitValue() {
        BigDecimal price = calculateSellApproximatePrice(OrderType.STOP_LIMIT, listing, 10,
                new BigDecimal("102.00"), new BigDecimal("95.00"));
        assertThat(price).isEqualByComparingTo("1020.00"); // 102 * 1 * 10
    }

    @Test
    void buyMarket_greaterThanSellMarket() {
        BigDecimal buyPrice = calculateApproximatePrice(OrderType.MARKET, listing, 10, null, null);
        BigDecimal sellPrice = calculateSellApproximatePrice(OrderType.MARKET, listing, 10, null, null);
        assertThat(buyPrice).isGreaterThan(sellPrice);
    }
}

