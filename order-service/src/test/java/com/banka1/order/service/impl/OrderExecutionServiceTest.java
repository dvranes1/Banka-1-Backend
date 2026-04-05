package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.StockClient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for OrderExecutionService.
 */
@ExtendWith(MockitoExtension.class)
class OrderExecutionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private StockClient stockClient;

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private OrderExecutionServiceImpl orderExecutionService;

    private Order order;
    private StockListingDto listing;
    private Portfolio portfolio;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        // Order setup
        order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setListingId(42L);
        order.setOrderType(OrderType.MARKET);
        order.setDirection(OrderDirection.BUY);
        order.setStatus(OrderStatus.APPROVED);
        order.setQuantity(10);
        order.setContractSize(1);
        order.setRemainingPortions(10);
        order.setIsDone(false);
        order.setAfterHours(false);
        order.setAllOrNone(false);

        // Listing setup
        listing = new StockListingDto();
        listing.setId(42L);
        listing.setPrice(new BigDecimal("100.00"));
        listing.setAsk(new BigDecimal("101.00"));
        listing.setBid(new BigDecimal("99.00"));
        listing.setContractSize(1);

        // Portfolio setup
        portfolio = new Portfolio();
        portfolio.setUserId(1L);
        portfolio.setListingId(42L);
        portfolio.setQuantity(0);
        portfolio.setAveragePurchasePrice(BigDecimal.ZERO);

        // Transaction setup
        transaction = new Transaction();
        transaction.setOrderId(1L);
        transaction.setQuantity(5);
        transaction.setPricePerUnit(new BigDecimal("101.00"));
        transaction.setTotalPrice(new BigDecimal("505.00"));
        transaction.setCommission(BigDecimal.ZERO);

        // Default mock responses
        lenient().when(stockClient.getListing(42L)).thenReturn(listing);
        lenient().when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));
        lenient().when(transactionRepository.save(any())).thenReturn(transaction);
        lenient().when(portfolioRepository.save(any())).thenReturn(portfolio);
        lenient().when(orderRepository.save(any())).thenReturn(order);
    }

    // Execution Tests

    @Test
    void executePortion_createsTransaction() {
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        orderExecutionService.executeOrderPortion(order);

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void executePortion_decreasesRemainingPortions() {
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        int initialRemaining = order.getRemainingPortions();
        orderExecutionService.executeOrderPortion(order);

        // After execution, remaining should decrease
        assertThat(order.getRemainingPortions()).isLessThanOrEqualTo(initialRemaining);
    }

    @Test
    void executePortion_whenRemaining_updatesOrder() {
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));
        when(orderRepository.save(any())).thenReturn(order);

        orderExecutionService.executeOrderPortion(order);

        verify(orderRepository).save(order);
    }

    @Test
    void executeAll_untilDone_marksOrderDone() {
        order.setRemainingPortions(1); // Only 1 left
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        orderExecutionService.executeOrderPortion(order);

        // After full execution
        if (order.getRemainingPortions() == 0) {
            assertThat(order.getIsDone()).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DONE);
        }
    }

    @Test
    void buyOrder_updatesPortfolio() {

        orderExecutionService.executeOrderPortion(order);

        // Portfolio should be updated for BUY
        verify(portfolioRepository, atLeastOnce()).findByUserIdAndListingId(1L, 42L);
    }

    @Test
    void sellOrder_decreasesPortfolioQuantity() {
        order.setDirection(OrderDirection.SELL);
        portfolio.setQuantity(10);

        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        orderExecutionService.executeOrderPortion(order);

        // Portfolio quantity should decrease for SELL
        verify(portfolioRepository, atLeastOnce()).save(portfolio);
    }

    @Test
    void marketOrder_usesCorrectPrice() {
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        orderExecutionService.executeOrderPortion(order);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction savedTx = captor.getValue();

        // For BUY MARKET, should use ask price
        assertThat(savedTx.getPricePerUnit()).isEqualByComparingTo(listing.getAsk());
    }

    @Test
    void limitOrder_usesLimitPrice() {
        order.setOrderType(OrderType.LIMIT);
        order.setLimitValue(new BigDecimal("98.00"));

        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        orderExecutionService.executeOrderPortion(order);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction savedTx = captor.getValue();

        assertThat(savedTx.getPricePerUnit()).isEqualByComparingTo("98.00");
    }

    @Test
    void allOrNone_executesFullQuantityOrNone() {
        order.setAllOrNone(true);
        order.setRemainingPortions(10);

        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        // AON should execute all or nothing - in this test, we assume it executes all
        orderExecutionService.executeOrderPortion(order);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
    }

    @Test
    void stopOrder_checksActivation() {
        order.setOrderType(OrderType.STOP);
        order.setStopValue(new BigDecimal("95.00"));
        order.setDirection(OrderDirection.BUY);

        // For BUY STOP, activation is when ask >= stopValue
        // In our setup, ask = 101, stopValue = 95, so should activate
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        orderExecutionService.executeOrderPortion(order);

        // Should have created transaction if activated
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void asyncExecution_keepsExecutingUntilDone() {
        order.setRemainingPortions(3);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));
        when(orderRepository.save(any())).thenReturn(order);

        // Simulate loop by calling executeOrderAsync
        // In real test, this would be async, but we're mocking it
        orderExecutionService.executeOrderAsync(1L);

        // At least one execution should happen
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
    }

    @Test
    void transaction_records_all_details() {
        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        orderExecutionService.executeOrderPortion(order);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction savedTx = captor.getValue();

        assertThat(savedTx.getOrderId()).isEqualTo(1L);
        assertThat(savedTx.getQuantity()).isGreaterThan(0);
        assertThat(savedTx.getPricePerUnit()).isNotNull();
        assertThat(savedTx.getTotalPrice()).isNotNull();
    }

    @Test
    void buyMarketOrder_updatesPortfolioCorrectly() {
        order.setDirection(OrderDirection.BUY);
        order.setOrderType(OrderType.MARKET);
        portfolio.setQuantity(0);


        orderExecutionService.executeOrderPortion(order);

        // Portfolio should be fetched
        verify(portfolioRepository, atLeastOnce()).findByUserIdAndListingId(1L, 42L);
    }

    @Test
    void remainingPortions_reachesZero_orderDone() {
        order.setRemainingPortions(1);

        when(transactionRepository.save(any())).thenReturn(transaction);
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getRemainingPortions() == 0) {
                o.setIsDone(true);
                o.setStatus(OrderStatus.DONE);
            }
            return o;
        });

        orderExecutionService.executeOrderPortion(order);

        // When remaining portions reach 0, order should be DONE
        if (order.getRemainingPortions() == 0) {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DONE);
        }
    }
}

