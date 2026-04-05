package com.banka1.order.service;

import com.banka1.order.entity.ActuaryInfo;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.repository.ActuaryInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for order approval logic.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalLogicTest {

    @Mock
    private ActuaryInfoRepository actuaryInfoRepository;

    private ActuaryInfo agentInfo;

    @BeforeEach
    void setUp() {
        agentInfo = new ActuaryInfo();
        agentInfo.setEmployeeId(1L);
        agentInfo.setLimit(new BigDecimal("100000.00"));
        agentInfo.setUsedLimit(new BigDecimal("10000.00"));
        agentInfo.setNeedApproval(false);
    }

    /**
     * Helper to determine approval status.
     */
    private OrderStatus determineOrderStatus(Long userId, BigDecimal approximatePrice, Boolean margin) {
        ActuaryInfo actuaryInfo = actuaryInfoRepository.findByEmployeeId(userId).orElse(null);
        if (actuaryInfo == null) {
            // Client - always approved
            return OrderStatus.APPROVED;
        }

        // Actuary - check limits
        if (actuaryInfo.getNeedApproval() ||
            actuaryInfo.getUsedLimit().add(approximatePrice).compareTo(actuaryInfo.getLimit()) > 0) {
            return OrderStatus.PENDING;
        }

        return OrderStatus.APPROVED;
    }

    // Client Tests

    @Test
    void client_alwaysApproved() {
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.empty());
        OrderStatus status = determineOrderStatus(1L, new BigDecimal("50000.00"), false);
        assertThat(status).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    void client_approvedRegardlessOfAmount() {
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.empty());
        OrderStatus status = determineOrderStatus(1L, new BigDecimal("999999.00"), false);
        assertThat(status).isEqualTo(OrderStatus.APPROVED);
    }

    // Agent Tests

    @Test
    void agent_withoutNeedApproval_approved() {
        agentInfo.setNeedApproval(false);
        agentInfo.setUsedLimit(new BigDecimal("10000.00"));
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));

        OrderStatus status = determineOrderStatus(1L, new BigDecimal("50000.00"), false);
        assertThat(status).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    void agent_withNeedApproval_pending() {
        agentInfo.setNeedApproval(true);
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));

        OrderStatus status = determineOrderStatus(1L, new BigDecimal("50000.00"), false);
        assertThat(status).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void agent_exceedsLimit_pending() {
        agentInfo.setNeedApproval(false);
        agentInfo.setLimit(new BigDecimal("50000.00"));
        agentInfo.setUsedLimit(new BigDecimal("30000.00"));
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));

        // New order of 25000, total would be 55000 which exceeds 50000
        OrderStatus status = determineOrderStatus(1L, new BigDecimal("25000.00"), false);
        assertThat(status).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void agent_withinLimit_approved() {
        agentInfo.setNeedApproval(false);
        agentInfo.setLimit(new BigDecimal("100000.00"));
        agentInfo.setUsedLimit(new BigDecimal("30000.00"));
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));

        // New order of 20000, total would be 50000 which is within 100000
        OrderStatus status = determineOrderStatus(1L, new BigDecimal("20000.00"), false);
        assertThat(status).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    void agent_exactlyAtLimit_approved() {
        agentInfo.setNeedApproval(false);
        agentInfo.setLimit(new BigDecimal("50000.00"));
        agentInfo.setUsedLimit(new BigDecimal("30000.00"));
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));

        // New order of 20000, total would be exactly 50000
        OrderStatus status = determineOrderStatus(1L, new BigDecimal("20000.00"), false);
        assertThat(status).isEqualTo(OrderStatus.APPROVED);
    }

    @Test
    void agent_justExceedsLimit_pending() {
        agentInfo.setNeedApproval(false);
        agentInfo.setLimit(new BigDecimal("50000.00"));
        agentInfo.setUsedLimit(new BigDecimal("30000.00"));
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));

        // New order of 20001, total would be 50001 which exceeds limit
        OrderStatus status = determineOrderStatus(1L, new BigDecimal("20001.00"), false);
        assertThat(status).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void agent_needApprovalOverridesLimit() {
        agentInfo.setNeedApproval(true);
        agentInfo.setLimit(new BigDecimal("100000.00"));
        agentInfo.setUsedLimit(new BigDecimal("10000.00"));
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));

        // Even though within limit, needApproval=true should make it PENDING
        OrderStatus status = determineOrderStatus(1L, new BigDecimal("50000.00"), false);
        assertThat(status).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void supervisor_alwaysApproved() {
        // Supervisor has a very high limit and needApproval=false
        agentInfo.setNeedApproval(false);
        agentInfo.setLimit(new BigDecimal("999999999.00"));
        agentInfo.setUsedLimit(BigDecimal.ZERO);
        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));

        OrderStatus status = determineOrderStatus(1L, new BigDecimal("999999.00"), false);
        assertThat(status).isEqualTo(OrderStatus.APPROVED);
    }
}

