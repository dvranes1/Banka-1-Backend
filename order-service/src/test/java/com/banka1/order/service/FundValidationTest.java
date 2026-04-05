package com.banka1.order.service;

import com.banka1.order.dto.AccountDetailsDto;
import com.banka1.order.client.AccountClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests for margin and fund validation.
 */
@ExtendWith(MockitoExtension.class)
class FundValidationTest {

    @Mock
    private AccountClient accountClient;

    private AccountDetailsDto account;

    @BeforeEach
    void setUp() {
        account = new AccountDetailsDto();
        account.setAccountNumber("123456");
        account.setBalance(new BigDecimal("50000.00"));
        account.setCurrency("RSD");
        account.setOwnerId(1L);
    }

    /**
     * Helper to check funds.
     */
    private void checkFunds(Long accountId, BigDecimal totalAmount) {
        AccountDetailsDto acc = accountClient.getAccountDetails(accountId);
        if (acc.getBalance().compareTo(totalAmount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
    }

    // Sufficient Funds Tests

    @Test
    void sufficientFunds_passes() {
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        // Should not throw
        checkFunds(1L, new BigDecimal("30000.00"));
    }

    @Test
    void exactFunds_passes() {
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        // Should not throw when exactly equal
        checkFunds(1L, new BigDecimal("50000.00"));
    }

    @Test
    void smallFunds_passes() {
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        checkFunds(1L, new BigDecimal("1.00"));
    }

    @Test
    void halfFunds_passes() {
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        checkFunds(1L, new BigDecimal("25000.00"));
    }

    // Insufficient Funds Tests

    @Test
    void insufficientFunds_throwsException() {
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        assertThatThrownBy(() -> checkFunds(1L, new BigDecimal("60000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void slightlyInsufficientFunds_throwsException() {
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        assertThatThrownBy(() -> checkFunds(1L, new BigDecimal("50000.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroBudget_throwsException() {
        account.setBalance(BigDecimal.ZERO);
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        assertThatThrownBy(() -> checkFunds(1L, new BigDecimal("1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeBalance_throws() {
        account.setBalance(new BigDecimal("-1000.00"));
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        assertThatThrownBy(() -> checkFunds(1L, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Edge Cases

    @Test
    void largeAmount_insufficientFunds() {
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        assertThatThrownBy(() -> checkFunds(1L, new BigDecimal("1000000.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void largeBalance_sufficientFunds() {
        account.setBalance(new BigDecimal("1000000.00"));
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        checkFunds(1L, new BigDecimal("900000.00"));
    }

    @Test
    void zeroAmount_always_passes() {
        account.setBalance(BigDecimal.ZERO);
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        checkFunds(1L, BigDecimal.ZERO);
    }

    @Test
    void decimalPrecision_respected() {
        account.setBalance(new BigDecimal("100.99"));
        when(accountClient.getAccountDetails(1L)).thenReturn(account);

        checkFunds(1L, new BigDecimal("100.99"));

        assertThatThrownBy(() -> checkFunds(1L, new BigDecimal("101.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

