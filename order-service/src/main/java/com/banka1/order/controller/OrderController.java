package com.banka1.order.controller;

import com.banka1.order.dto.CreateBuyOrderRequest;
import com.banka1.order.dto.CreateSellOrderRequest;
import com.banka1.order.dto.OrderResponse;
import com.banka1.order.service.OrderCreationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing brokerage orders.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCreationService orderCreationService;

    /**
     * Creates a new buy order.
     *
     * @param jwt the authenticated user
     * @param request the buy order details
     * @return the created order response
     */
    @PostMapping("/buy")
    @PreAuthorize("hasRole('CLIENT') or hasRole('ACTUARY')")
    public ResponseEntity<OrderResponse> createBuyOrder(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestBody CreateBuyOrderRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        OrderResponse response = orderCreationService.createBuyOrder(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new sell order.
     *
     * @param jwt the authenticated user
     * @param request the sell order details
     * @return the created order response
     */
    @PostMapping("/sell")
    @PreAuthorize("hasRole('CLIENT') or hasRole('ACTUARY')")
    public ResponseEntity<OrderResponse> createSellOrder(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestBody CreateSellOrderRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        OrderResponse response = orderCreationService.createSellOrder(userId, request);
        return ResponseEntity.ok(response);
    }
}
