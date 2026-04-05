package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.EmployeeClient;
import com.banka1.order.client.ExchangeClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.*;
import com.banka1.order.entity.ActuaryInfo;
import com.banka1.order.entity.Order;
import com.banka1.order.entity.Portfolio;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import com.banka1.order.repository.ActuaryInfoRepository;
import com.banka1.order.repository.OrderRepository;
import com.banka1.order.repository.PortfolioRepository;
import com.banka1.order.service.OrderCreationService;
import com.banka1.order.service.OrderExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implementation of OrderCreationService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCreationServiceImpl implements OrderCreationService {

    private final OrderRepository orderRepository;
    private final ActuaryInfoRepository actuaryInfoRepository;
    private final PortfolioRepository portfolioRepository;
    private final StockClient stockClient;
    private final AccountClient accountClient;
    private final EmployeeClient employeeClient;
    private final ExchangeClient exchangeClient;
    private final OrderExecutionService orderExecutionService;

    @Override
    @Transactional
    public OrderResponse createBuyOrder(Long userId, CreateBuyOrderRequest request) {
        // Validate request
        validateBuyOrderRequest(request);

        // Get listing details
        StockListingDto listing = stockClient.getListing(request.getListingId());

        // Check exchange status
        ExchangeStatusDto exchangeStatus = stockClient.getExchangeStatus(listing.getExchangeId());
        boolean afterHours = exchangeStatus.isAfterHours();

        // Determine order type
        OrderType orderType = determineOrderType(request.getLimitValue(), request.getStopValue());

        // Calculate approximate price
        BigDecimal approximatePrice = calculateApproximatePrice(orderType, listing, request.getQuantity(), request.getLimitValue(), request.getStopValue());

        // Check user type and approval
        OrderStatus status = determineOrderStatus(userId, approximatePrice, request.getMargin());

        // Check margin if applicable
        if (request.getMargin()) {
            checkMarginRequirements(userId, request.getAccountId(), approximatePrice);
        }

        // Check funds
        BigDecimal fee = calculateFee(orderType, approximatePrice);
        checkFunds(request.getAccountId(), approximatePrice.add(fee));

        // Transfer fee
        transferFee(request.getAccountId(), fee);

        // Create order
        Order order = new Order();
        order.setUserId(userId);
        order.setListingId(request.getListingId());
        order.setOrderType(orderType);
        order.setQuantity(request.getQuantity());
        order.setContractSize(listing.getContractSize());
        order.setPricePerUnit(getPricePerUnit(orderType, listing, request.getLimitValue(), request.getStopValue()));
        order.setLimitValue(request.getLimitValue());
        order.setStopValue(request.getStopValue());
        order.setDirection(OrderDirection.BUY);
        order.setStatus(status);
        order.setApprovedBy(null); // Set later if approved
        order.setIsDone(false);
        order.setRemainingPortions(request.getQuantity());
        order.setAfterHours(afterHours);
        order.setAllOrNone(request.getAllOrNone());
        order.setMargin(request.getMargin());
        order.setAccountId(request.getAccountId());

        order = orderRepository.save(order);

        // If approved, start execution
        if (status == OrderStatus.APPROVED) {
            orderExecutionService.executeOrderAsync(order.getId());
        }

        return mapToResponse(order, approximatePrice, fee);
    }

    @Override
    @Transactional
    public OrderResponse createSellOrder(Long userId, CreateSellOrderRequest request) {
        // Validate request
        validateSellOrderRequest(request);

        // Check portfolio
        PortfolioDto portfolio = getPortfolio(userId, request.getListingId());
        if (portfolio.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException("Insufficient portfolio quantity");
        }

        // Get listing details
        StockListingDto listing = stockClient.getListing(request.getListingId());

        // Check exchange status
        ExchangeStatusDto exchangeStatus = stockClient.getExchangeStatus(listing.getExchangeId());
        boolean afterHours = exchangeStatus.isAfterHours();

        // Determine order type
        OrderType orderType = determineOrderType(request.getLimitValue(), request.getStopValue());

        // Calculate approximate price
        BigDecimal approximatePrice = calculateSellApproximatePrice(orderType, listing, request.getQuantity(), request.getLimitValue(), request.getStopValue());

        // Check user type and approval
        OrderStatus status = determineOrderStatus(userId, approximatePrice, request.getMargin());

        // Check margin if applicable
        if (request.getMargin()) {
            checkMarginRequirements(userId, request.getAccountId(), approximatePrice);
        }

        // Calculate fee
        BigDecimal fee = calculateFee(orderType, approximatePrice);

        // Transfer fee
        transferFee(request.getAccountId(), fee);

        // Create order
        Order order = new Order();
        order.setUserId(userId);
        order.setListingId(request.getListingId());
        order.setOrderType(orderType);
        order.setQuantity(request.getQuantity());
        order.setContractSize(listing.getContractSize());
        order.setPricePerUnit(getSellPricePerUnit(orderType, listing, request.getLimitValue(), request.getStopValue()));
        order.setLimitValue(request.getLimitValue());
        order.setStopValue(request.getStopValue());
        order.setDirection(OrderDirection.SELL);
        order.setStatus(status);
        order.setApprovedBy(null);
        order.setIsDone(false);
        order.setRemainingPortions(request.getQuantity());
        order.setAfterHours(afterHours);
        order.setAllOrNone(request.getAllOrNone());
        order.setMargin(request.getMargin());
        order.setAccountId(request.getAccountId());

        order = orderRepository.save(order);

        // If approved, start execution
        if (status == OrderStatus.APPROVED) {
            orderExecutionService.executeOrderAsync(order.getId());
        }

        return mapToResponse(order, approximatePrice, fee);
    }

    private void validateBuyOrderRequest(CreateBuyOrderRequest request) {
        if (request.getListingId() == null || request.getQuantity() == null || request.getQuantity() <= 0 ||
            request.getAccountId() == null) {
            throw new IllegalArgumentException("Invalid request parameters");
        }
        if (request.getLimitValue() != null && request.getLimitValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Limit value must be positive");
        }
        if (request.getStopValue() != null && request.getStopValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Stop value must be positive");
        }
    }

    private void validateSellOrderRequest(CreateSellOrderRequest request) {
        if (request.getListingId() == null || request.getQuantity() == null || request.getQuantity() <= 0 ||
            request.getAccountId() == null) {
            throw new IllegalArgumentException("Invalid request parameters");
        }
        if (request.getLimitValue() != null && request.getLimitValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Limit value must be positive");
        }
        if (request.getStopValue() != null && request.getStopValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Stop value must be positive");
        }
    }

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

    private BigDecimal calculateApproximatePrice(OrderType orderType, StockListingDto listing, Integer quantity, BigDecimal limitValue, BigDecimal stopValue) {
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

    private BigDecimal calculateSellApproximatePrice(OrderType orderType, StockListingDto listing, Integer quantity, BigDecimal limitValue, BigDecimal stopValue) {
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

    private OrderStatus determineOrderStatus(Long userId, BigDecimal approximatePrice, Boolean margin) {
        // Check if user is actuary
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

    private void checkMarginRequirements(Long userId, Long accountId, BigDecimal approximatePrice) {
        // TODO: Implement margin check
        // Get account details, check credit, calculate initial margin cost = maintenance margin * 1.1
    }

    private void checkFunds(Long accountId, BigDecimal totalAmount) {
        AccountDetailsDto account = accountClient.getAccountDetails(accountId);
        if (account.getBalance().compareTo(totalAmount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
    }

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

    private void transferFee(Long accountId, BigDecimal fee) {
        // Get bank account
        EmployeeDto bankAccount = employeeClient.getBankAccount("RSD");
        // Transfer fee
        AccountTransactionRequest transferRequest = new AccountTransactionRequest();
        transferRequest.setFromAccountId(accountId);
        transferRequest.setToAccountId(bankAccount.getId());
        transferRequest.setAmount(fee);
        transferRequest.setCurrency("RSD");
        transferRequest.setDescription("Order fee");
        accountClient.transfer(transferRequest);
    }

    private BigDecimal getPricePerUnit(OrderType orderType, StockListingDto listing, BigDecimal limitValue, BigDecimal stopValue) {
        switch (orderType) {
            case MARKET:
                return listing.getPrice();
            case LIMIT:
                return limitValue;
            case STOP:
                return stopValue;
            case STOP_LIMIT:
                return limitValue;
            default:
                throw new IllegalArgumentException("Unknown order type");
        }
    }

    private BigDecimal getSellPricePerUnit(OrderType orderType, StockListingDto listing, BigDecimal limitValue, BigDecimal stopValue) {
        switch (orderType) {
            case MARKET:
                return listing.getBid();
            case LIMIT:
                return limitValue;
            case STOP:
                return stopValue;
            case STOP_LIMIT:
                return limitValue;
            default:
                throw new IllegalArgumentException("Unknown order type");
        }
    }

    private PortfolioDto getPortfolio(Long userId, Long listingId) {
        Portfolio portfolio = portfolioRepository.findByUserIdAndListingId(userId, listingId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio position not found"));
        PortfolioDto dto = new PortfolioDto();
        dto.setId(portfolio.getId());
        dto.setUserId(portfolio.getUserId());
        dto.setListingId(portfolio.getListingId());
        dto.setQuantity(portfolio.getQuantity());
        dto.setAveragePurchasePrice(portfolio.getAveragePurchasePrice());
        return dto;
    }

    private OrderResponse mapToResponse(Order order, BigDecimal approximatePrice, BigDecimal fee) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setUserId(order.getUserId());
        response.setListingId(order.getListingId());
        response.setOrderType(order.getOrderType());
        response.setQuantity(order.getQuantity());
        response.setContractSize(order.getContractSize());
        response.setPricePerUnit(order.getPricePerUnit());
        response.setLimitValue(order.getLimitValue());
        response.setStopValue(order.getStopValue());
        response.setDirection(order.getDirection());
        response.setStatus(order.getStatus());
        response.setApprovedBy(order.getApprovedBy());
        response.setIsDone(order.getIsDone());
        response.setLastModification(order.getLastModification());
        response.setRemainingPortions(order.getRemainingPortions());
        response.setAfterHours(order.getAfterHours());
        response.setAllOrNone(order.getAllOrNone());
        response.setMargin(order.getMargin());
        response.setAccountId(order.getAccountId());
        response.setApproximatePrice(approximatePrice);
        response.setFee(fee);
        return response;
    }
}
