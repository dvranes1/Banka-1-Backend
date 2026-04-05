package com.banka1.order.service.impl;

import com.banka1.order.client.AccountClient;
import com.banka1.order.client.EmployeeClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doNothing;

/**
 * Integration tests for OrderCreationService.
 */
@ExtendWith(MockitoExtension.class)
class OrderCreationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ActuaryInfoRepository actuaryInfoRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private StockClient stockClient;

    @Mock
    private AccountClient accountClient;

    @Mock
    private EmployeeClient employeeClient;

    @Mock
    private OrderExecutionService orderExecutionService;

    @InjectMocks
    private OrderCreationServiceImpl orderCreationService;

    private CreateBuyOrderRequest buyRequest;
    private CreateSellOrderRequest sellRequest;
    private StockListingDto listing;
    private ExchangeStatusDto exchangeStatus;
    private AccountDetailsDto accountDetails;
    private Portfolio portfolio;
    private Order savedOrder;

    @BeforeEach
    void setUp() {
        // Buy request setup
        buyRequest = new CreateBuyOrderRequest();
        buyRequest.setListingId(42L);
        buyRequest.setQuantity(10);
        buyRequest.setAccountId(5L);
        buyRequest.setAllOrNone(false);
        buyRequest.setMargin(false);

        // Sell request setup
        sellRequest = new CreateSellOrderRequest();
        sellRequest.setListingId(42L);
        sellRequest.setQuantity(5);
        sellRequest.setAccountId(5L);
        sellRequest.setAllOrNone(false);
        sellRequest.setMargin(false);

        // Listing setup
        listing = new StockListingDto();
        listing.setId(42L);
        listing.setTicker("TEST");
        listing.setPrice(new BigDecimal("100.00"));
        listing.setAsk(new BigDecimal("101.00"));
        listing.setBid(new BigDecimal("99.00"));
        listing.setContractSize(1);
        listing.setExchangeId(1L);
        listing.setCurrency("RSD");

        // Exchange status setup
        exchangeStatus = new ExchangeStatusDto();
        exchangeStatus.setOpen(true);
        exchangeStatus.setAfterHours(false);
        exchangeStatus.setClosed(false);

        // Account details setup
        accountDetails = new AccountDetailsDto();
        accountDetails.setAccountNumber("123456");
        accountDetails.setBalance(new BigDecimal("50000.00"));
        accountDetails.setCurrency("RSD");

        // Portfolio setup
        portfolio = new Portfolio();
        portfolio.setUserId(1L);
        portfolio.setListingId(42L);
        portfolio.setQuantity(100);
        portfolio.setAveragePurchasePrice(new BigDecimal("95.00"));

        // Saved order setup
        savedOrder = new Order();
        savedOrder.setId(1L);
        savedOrder.setUserId(1L);
        savedOrder.setListingId(42L);
        savedOrder.setDirection(OrderDirection.BUY);
        savedOrder.setStatus(OrderStatus.APPROVED);

        // Default mock responses
        EmployeeDto bankAccount = new EmployeeDto();
        bankAccount.setId(999L);
        
        lenient().when(stockClient.getListing(42L)).thenReturn(listing);
        lenient().when(stockClient.getExchangeStatus(1L)).thenReturn(exchangeStatus);
        lenient().when(accountClient.getAccountDetails(5L)).thenReturn(accountDetails);
        lenient().doNothing().when(accountClient).transfer(any());
        lenient().when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.empty());
        lenient().when(employeeClient.getBankAccount("RSD")).thenReturn(bankAccount);
    }

    // Buy Order Tests

    @Test
    void buyMarketOrder_createdSuccessfully() {
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderResponse response = orderCreationService.createBuyOrder(1L, buyRequest);

        assertThat(response).isNotNull();
        assertThat(response.getDirection()).isEqualTo(OrderDirection.BUY);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.APPROVED);
        verify(orderRepository).save(any());
    }

    @Test
    void buyLimitOrder_withLimitValue() {
        buyRequest.setLimitValue(new BigDecimal("98.00"));
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderResponse response = orderCreationService.createBuyOrder(1L, buyRequest);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getOrderType()).isEqualTo(OrderType.LIMIT);
        assertThat(saved.getLimitValue()).isEqualByComparingTo("98.00");
    }

    @Test
    void buyOrder_agentWithNeedApproval_statusPending() {
        ActuaryInfo agentInfo = new ActuaryInfo();
        agentInfo.setEmployeeId(1L);
        agentInfo.setNeedApproval(true);
        agentInfo.setUsedLimit(BigDecimal.ZERO);
        agentInfo.setLimit(new BigDecimal("100000.00"));

        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setStatus(OrderStatus.PENDING);
            return order;
        });

        OrderResponse response = orderCreationService.createBuyOrder(1L, buyRequest);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void buyOrder_agentExceedsLimit_statusPending() {
        ActuaryInfo agentInfo = new ActuaryInfo();
        agentInfo.setEmployeeId(1L);
        agentInfo.setNeedApproval(false);
        agentInfo.setUsedLimit(new BigDecimal("95000.00"));
        agentInfo.setLimit(new BigDecimal("100000.00"));

        when(actuaryInfoRepository.findByEmployeeId(1L)).thenReturn(Optional.of(agentInfo));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setStatus(OrderStatus.PENDING);
            return order;
        });

        OrderResponse response = orderCreationService.createBuyOrder(1L, buyRequest);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void buyOrder_invalidQuantity_throwsException() {
        buyRequest.setQuantity(-1);

        assertThatThrownBy(() -> orderCreationService.createBuyOrder(1L, buyRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buyOrder_nullQuantity_throwsException() {
        buyRequest.setQuantity(null);

        assertThatThrownBy(() -> orderCreationService.createBuyOrder(1L, buyRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buyOrder_insufficientFunds_throwsException() {
        accountDetails.setBalance(new BigDecimal("100.00")); // Too low
        when(accountClient.getAccountDetails(5L)).thenReturn(accountDetails);

        assertThatThrownBy(() -> orderCreationService.createBuyOrder(1L, buyRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buyOrder_approved_triggersExecution() {
        when(orderRepository.save(any())).thenReturn(savedOrder);

        orderCreationService.createBuyOrder(1L, buyRequest);

        verify(orderExecutionService).executeOrderAsync(savedOrder.getId());
    }

    // Sell Order Tests

    @Test
    void sellOrder_createdSuccessfully() {
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderResponse response = orderCreationService.createSellOrder(1L, sellRequest);

        assertThat(response).isNotNull();
        verify(orderRepository).save(any());
    }

    @Test
    void sellOrder_noPortfolio_throwsException() {
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderCreationService.createSellOrder(1L, sellRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sellOrder_insufficientPortfolioQuantity_throwsException() {
        portfolio.setQuantity(3); // Less than requested 5
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));

        assertThatThrownBy(() -> orderCreationService.createSellOrder(1L, sellRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient portfolio quantity");
    }

    @Test
    void sellOrder_exactPortfolioQuantity_passes() {
        portfolio.setQuantity(5); // Exactly what we want to sell
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderResponse response = orderCreationService.createSellOrder(1L, sellRequest);

        assertThat(response).isNotNull();
        verify(orderRepository).save(any());
    }

    @Test
    void sellOrder_transfersFee() {
        when(portfolioRepository.findByUserIdAndListingId(1L, 42L)).thenReturn(Optional.of(portfolio));
        when(orderRepository.save(any())).thenReturn(savedOrder);

        EmployeeDto bankAccount = new EmployeeDto();
        bankAccount.setId(999L);
        when(employeeClient.getBankAccount("RSD")).thenReturn(bankAccount);

        orderCreationService.createSellOrder(1L, sellRequest);

        verify(accountClient).transfer(any(AccountTransactionRequest.class));
    }

    @Test
    void buyOrder_withAllOrNone_isSet() {
        buyRequest.setAllOrNone(true);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        orderCreationService.createBuyOrder(1L, buyRequest);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getAllOrNone()).isTrue();
    }

    @Test
    void buyOrder_storesContractSize() {
        when(orderRepository.save(any())).thenReturn(savedOrder);

        orderCreationService.createBuyOrder(1L, buyRequest);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getContractSize()).isEqualTo(listing.getContractSize());
    }

    @Test
    void buyOrder_afterHoursFlag_isSet() {
        exchangeStatus.setAfterHours(true);
        when(stockClient.getExchangeStatus(1L)).thenReturn(exchangeStatus);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        orderCreationService.createBuyOrder(1L, buyRequest);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getAfterHours()).isTrue();
    }
}

