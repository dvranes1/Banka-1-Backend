package com.banka1.order.service;

import com.banka1.order.dto.CreateBuyOrderRequest;
import com.banka1.order.dto.CreateSellOrderRequest;
import com.banka1.order.dto.OrderResponse;

/**
 * Service for creating buy and sell orders.
 */
public interface OrderCreationService {

    /**
     * Creates a buy order with validation and approval logic.
     *
     * @param userId the ID of the user placing the order
     * @param request the buy order request
     * @return the created order response
     */
    OrderResponse createBuyOrder(Long userId, CreateBuyOrderRequest request);

    /**
     * Creates a sell order with validation and approval logic.
     *
     * @param userId the ID of the user placing the order
     * @param request the sell order request
     * @return the created order response
     */
    OrderResponse createSellOrder(Long userId, CreateSellOrderRequest request);
}
