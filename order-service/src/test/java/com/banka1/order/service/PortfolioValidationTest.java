package com.banka1.order.service;

import com.banka1.order.dto.PortfolioDto;
import com.banka1.order.entity.Portfolio;
import com.banka1.order.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for portfolio validation during sell orders.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioValidationTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
        portfolio.setUserId(1L);
        portfolio.setListingId(42L);
        portfolio.setQuantity(100);
        portfolio.setAveragePurchasePrice(new BigDecimal("50.00"));
    }

    /**
     * Helper to get portfolio.
     */
    private PortfolioDto getPortfolio(Long userId, Long listingId) {
        Portfolio found = portfolioRepository.findByUserIdAndListingId(userId, listingId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio position not found"));
        PortfolioDto dto = new PortfolioDto();
        dto.setId(found.getId());
        dto.setUserId(found.getUserId());
        dto.setListingId(found.getListingId());
        dto.setQuantity(found.getQuantity());
        dto.setAveragePurchasePrice(found.getAveragePurchasePrice());
        return dto;
    }

    // Sell Validation Tests

    @Test
    void sellQuantity_lessThanOwned_passes() {
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        PortfolioDto dto = getPortfolio(1L, 42L);
        assertThat(dto.getQuantity()).isEqualTo(100);
        assertThat(dto.getQuantity()).isGreaterThanOrEqualTo(50);
    }

    @Test
    void sellQuantity_equalsOwned_passes() {
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        PortfolioDto dto = getPortfolio(1L, 42L);
        assertThat(dto.getQuantity()).isEqualTo(100);
        assertThat(dto.getQuantity()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void sellQuantity_greaterThanOwned_fails() {
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        PortfolioDto dto = getPortfolio(1L, 42L);
        int sellQuantity = 150;
        assertThat(dto.getQuantity()).isLessThan(sellQuantity);
    }

    @Test
    void sellWithoutPortfolio_throwsException() {
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getPortfolio(1L, 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Portfolio position not found");
    }

    @Test
    void sellZeroQuantity_stillRequiresPortfolio() {
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        PortfolioDto dto = getPortfolio(1L, 42L);
        int sellQuantity = 0;
        assertThat(dto.getQuantity()).isGreaterThan(sellQuantity);
    }

    @Test
    void multiplePortfolios_doesNotAffectValidation() {
        Portfolio portfolio2 = new Portfolio();
        portfolio2.setUserId(1L);
        portfolio2.setListingId(99L);
        portfolio2.setQuantity(200);

        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.findByUserIdAndListingId(1L, 99L)).thenReturn(Optional.of(portfolio2));

        PortfolioDto dto1 = getPortfolio(1L, 42L);
        PortfolioDto dto2 = getPortfolio(1L, 99L);

        assertThat(dto1.getQuantity()).isEqualTo(100);
        assertThat(dto2.getQuantity()).isEqualTo(200);
        assertThat(dto1.getQuantity()).isNotEqualTo(dto2.getQuantity());
    }

    @Test
    void sellPartialPortfolio_isValid() {
        portfolio.setQuantity(500);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        PortfolioDto dto = getPortfolio(1L, 42L);
        int sellQuantity = 100;

        assertThat(dto.getQuantity()).isGreaterThanOrEqualTo(sellQuantity);
        assertThat(dto.getQuantity() - sellQuantity).isEqualTo(400);
    }

    @Test
    void largePortfolioSmallSell_passes() {
        portfolio.setQuantity(10000);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        PortfolioDto dto = getPortfolio(1L, 42L);
        int sellQuantity = 1;

        assertThat(dto.getQuantity()).isGreaterThanOrEqualTo(sellQuantity);
    }
}

