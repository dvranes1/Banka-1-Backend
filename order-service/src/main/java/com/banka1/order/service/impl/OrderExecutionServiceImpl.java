package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.StockClient;
import com.banka1.order.dto.AccountTransactionRequest;
import com.banka1.order.dto.StockListingDto;
import com.banka1.order.entity.Order;
import com.banka1.order.entity.Portfolio;
import com.banka1.order.entity.Transaction;
import com.banka1.order.entity.enums.OrderDirection;
import com.banka1.order.entity.enums.OrderStatus;
import com.banka1.order.entity.enums.OrderType;
import com.banka1.order.repository.OrderRepository;
import com.banka1.order.repository.PortfolioRepository;
import com.banka1.order.repository.TransactionRepository;
import com.banka1.order.service.OrderExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * Implementation of OrderExecutionService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExecutionServiceImpl implements OrderExecutionService {

    private final OrderRepository orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final StockClient stockClient;
    private final AccountClient accountClient;

    private final Random random = new Random();

    @Override
    @Async
    public void executeOrderAsync(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.APPROVED) {
            return;
        }

        while (order.getRemainingPortions() > 0 && !order.getIsDone()) {
            executeOrderPortion(order);
            order = orderRepository.findById(orderId).get(); // Refresh

            // Calculate delay
            long delayMillis = calculateExecutionDelay(order);
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    @Transactional
    public void executeOrderPortion(Order order) {
        if (order.getRemainingPortions() <= 0 || order.getIsDone()) {
            return;
        }

        // Check if stop conditions are met
        if (!isOrderActivatable(order)) {
            return;
        }

        // Determine quantity to execute
        int quantityToExecute = determineExecutionQuantity(order);

        // Get current prices
        StockListingDto listing = stockClient.getListing(order.getListingId());
        BigDecimal executionPrice = calculateExecutionPrice(order, listing);

        // Execute the portion
        executePortion(order, quantityToExecute, executionPrice, listing);

        // Update order
        order.setRemainingPortions(order.getRemainingPortions() - quantityToExecute);
        if (order.getRemainingPortions() == 0) {
            order.setIsDone(true);
            order.setStatus(OrderStatus.DONE);
        }
        orderRepository.save(order);
    }

    private boolean isOrderActivatable(Order order) {
        if (order.getOrderType() == OrderType.MARKET || order.getOrderType() == OrderType.LIMIT) {
            return true;
        }

        StockListingDto listing = stockClient.getListing(order.getListingId());
        if (order.getOrderType() == OrderType.STOP) {
            if (order.getDirection() == OrderDirection.BUY) {
                return listing.getAsk().compareTo(order.getStopValue()) >= 0;
            } else {
                return listing.getBid().compareTo(order.getStopValue()) <= 0;
            }
        } else if (order.getOrderType() == OrderType.STOP_LIMIT) {
            if (order.getDirection() == OrderDirection.BUY) {
                return listing.getAsk().compareTo(order.getStopValue()) >= 0;
            } else {
                return listing.getBid().compareTo(order.getStopValue()) <= 0;
            }
        }
        return false;
    }

    private int determineExecutionQuantity(Order order) {
        if (order.getAllOrNone()) {
            return order.getRemainingPortions();
        } else {
            // Random between 1 and remaining
            return random.nextInt(order.getRemainingPortions()) + 1;
        }
    }

    private BigDecimal calculateExecutionPrice(Order order, StockListingDto listing) {
        switch (order.getOrderType()) {
            case MARKET:
                return order.getDirection() == OrderDirection.BUY ? listing.getAsk() : listing.getBid();
            case LIMIT:
                return order.getLimitValue();
            case STOP:
                return order.getDirection() == OrderDirection.BUY ? listing.getAsk() : listing.getBid();
            case STOP_LIMIT:
                return order.getLimitValue();
            default:
                throw new IllegalArgumentException("Unknown order type");
        }
    }

    private void executePortion(Order order, int quantity, BigDecimal executionPrice, StockListingDto listing) {
        BigDecimal totalAmount = executionPrice.multiply(BigDecimal.valueOf(order.getContractSize())).multiply(BigDecimal.valueOf(quantity));

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setOrderId(order.getId());
        transaction.setQuantity(quantity);
        transaction.setPricePerUnit(executionPrice);
        transaction.setTotalPrice(totalAmount);
        transaction.setCommission(BigDecimal.ZERO); // TODO: calculate commission
        transaction.setTimestamp(LocalDateTime.now());
        transactionRepository.save(transaction);

        // Update portfolio
        updatePortfolio(order, quantity, executionPrice);

        // Transfer funds
        transferFunds(order, totalAmount);
    }

    private void updatePortfolio(Order order, int quantity, BigDecimal executionPrice) {
        Portfolio portfolio = portfolioRepository.findByUserIdAndListingId(order.getUserId(), order.getListingId())
                .orElse(null);

        if (order.getDirection() == OrderDirection.BUY) {
            if (portfolio == null) {
                portfolio = new Portfolio();
                portfolio.setUserId(order.getUserId());
                portfolio.setListingId(order.getListingId());
                portfolio.setQuantity(quantity);
                portfolio.setAveragePurchasePrice(executionPrice);
                // TODO: set listingType
            } else {
                // Update average price
                BigDecimal totalValue = portfolio.getAveragePurchasePrice().multiply(BigDecimal.valueOf(portfolio.getQuantity()))
                        .add(executionPrice.multiply(BigDecimal.valueOf(quantity)));
                int newQuantity = portfolio.getQuantity() + quantity;
                portfolio.setAveragePurchasePrice(totalValue.divide(BigDecimal.valueOf(newQuantity), 4, RoundingMode.HALF_UP));
                portfolio.setQuantity(newQuantity);
            }
        } else { // SELL
            if (portfolio != null) {
                portfolio.setQuantity(portfolio.getQuantity() - quantity);
                if (portfolio.getQuantity() <= 0) {
                    portfolioRepository.delete(portfolio);
                } else {
                    portfolioRepository.save(portfolio);
                }
            }
        }
    }

    private void transferFunds(Order order, BigDecimal totalAmount) {
        AccountTransactionRequest request = new AccountTransactionRequest();
        request.setAmount(totalAmount);
        request.setCurrency("RSD"); // Assume RSD
        request.setDescription("Order execution");

        if (order.getDirection() == OrderDirection.BUY) {
            // Debit from user account
            request.setFromAccountId(order.getAccountId());
            request.setToAccountId(null); // TODO: to whom? Maybe not needed for buy
        } else {
            // Credit to user account
            request.setToAccountId(order.getAccountId());
            request.setFromAccountId(null); // TODO: from whom?
        }

        // TODO: Implement proper fund transfer
    }

    private long calculateExecutionDelay(Order order) {
        // Volume is not available, assume fixed
        int volume = 1000; // Placeholder
        int remaining = order.getRemainingPortions();
        double delaySeconds = random.nextDouble() * (24 * 60 * 60 / (volume / (double) remaining));
        if (order.getAfterHours()) {
            delaySeconds += 30 * 60; // +30 min
        }
        return (long) (delaySeconds * 1000);
    }
}
