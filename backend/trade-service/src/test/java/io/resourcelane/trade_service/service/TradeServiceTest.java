package io.resourcelane.trade_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.resourcelane.trade_service.dto.*;
import io.resourcelane.trade_service.model.Order;
import io.resourcelane.trade_service.model.OrderOutboxEvent;
import io.resourcelane.trade_service.model.ProcessedExecution;
import io.resourcelane.trade_service.model.Trade;
import io.resourcelane.trade_service.repository.OrderOutboxRepository;
import io.resourcelane.trade_service.repository.OrderReservationRepository;
import io.resourcelane.trade_service.repository.OrderRepository;
import io.resourcelane.trade_service.repository.ProcessedExecutionRepository;
import io.resourcelane.trade_service.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderOutboxRepository orderOutboxRepository;
    @Mock
    private OrderReservationRepository reservationRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ProcessedExecutionRepository processedExecutionRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TradeService tradeService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;
    @Captor
    private ArgumentCaptor<Trade> tradeCaptor;
    @Captor
    private ArgumentCaptor<OrderOutboxEvent> outboxCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tradeService, "walletServiceUrl", "http://localhost:8081/");
        ReflectionTestUtils.setField(tradeService, "internalSecretToken", "testSecret");
        SecurityContextHolder.clearContext();
    }

    private OrderRequest createOrderRequest(int quantity, Double price, String side, String type) {
        OrderRequest req = new OrderRequest();
        req.setQuantity(quantity);
        req.setPrice(price);
        req.setSide(side);
        req.setType(type);
        return req;
    }

    private Order createOrder(Long orderId, Long userId, Double price, int quantity, String side, String type) {
        Order o = new Order();
        o.setOrderId(orderId);
        o.setUserId(userId);
        o.setPrice(price);
        o.setQuantity(quantity);
        o.setSide(side);
        o.setType(type);
        o.setCreatedAt(Timestamp.from(Instant.now()));
        return o;
    }

    private Trade createTrade(Long tradeId, Long orderId, Long userId, Double price, int quantity, String side) {
        Trade t = new Trade();
        t.setTradeId(tradeId);
        t.setOrderId(orderId);
        t.setUserId(userId);
        t.setPrice(price);
        t.setQuantity(quantity);
        t.setSide(side);
        t.setCreatedAt(Timestamp.from(Instant.now()));
        return t;
    }

    private TradeConsumerDTO createTradeConsumerDTO(String buyUserId, String sellUserId, String buyOrderId, String sellOrderId, double price, int quantity) {
        TradeConsumerDTO dto = new TradeConsumerDTO();
        dto.setExecutionId("execution-1");
        dto.setBuyUserId(buyUserId);
        dto.setSellUserId(sellUserId);
        dto.setBuyOrderId(buyOrderId);
        dto.setSellOrderId(sellOrderId);
        dto.setPrice(price);
        dto.setQuantity(quantity);
        return dto;
    }

    private StatusConsumerDTO createStatusDTO(String orderId, String userId, String side, int quantity) {
        StatusConsumerDTO dto = new StatusConsumerDTO();
        dto.setOrderId(orderId);
        dto.setUserId(userId);
        dto.setSide(side);
        dto.setQuantity(quantity);
        return dto;
    }

    private void setupAuthContext(String jwt, String fingerprint) {
        AuthCredentials creds = new AuthCredentials();
        creds.setJwt(jwt);
        creds.setFingerprint(fingerprint);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(1L, creds);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    private void mockGetWalletResponse(Double balance, Double units) {
        WalletDTO wallet = new WalletDTO();
        wallet.setWalletId(1L);
        wallet.setUserId(1L);
        wallet.setBalance(balance);
        wallet.setUnits(units);
        ResponseEntity<WalletDTO> response = new ResponseEntity<>(wallet, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(WalletDTO.class)))
                .thenReturn(response);
    }

    // ── sendOrderToMatcher ──────────────────────────────────────────────

    @Test
    void sendOrderToMatcher_shouldSaveOrderAndQueueOutboxEvent() throws Exception {
        Long userId = 1L;
        OrderRequest request = createOrderRequest(10, 50000.0, "BUY", "LIMIT");
        Order savedOrder = createOrder(100L, userId, 50000.0, 10, "BUY", "LIMIT");
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(jdbcTemplate.queryForMap(anyString(), eq(userId)))
                .thenReturn(Map.of("balance", new java.math.BigDecimal("1000000.00"), "units", 10.0));

        tradeService.sendOrderToMatcher(request, userId);

        verify(orderRepository).save(orderCaptor.capture());
        Order captured = orderCaptor.getValue();
        assertThat(captured.getUserId()).isEqualTo(userId);
        assertThat(captured.getQuantity()).isEqualTo(10);
        assertThat(captured.getPrice()).isEqualTo(50000.0);
        assertThat(captured.getSide()).isEqualTo("BUY");
        assertThat(captured.getType()).isEqualTo("LIMIT");

        verify(orderOutboxRepository).save(outboxCaptor.capture());
        String json = outboxCaptor.getValue().getPayload();
        assertThat(outboxCaptor.getValue().getOrderId()).isEqualTo(100L);
        assertThat(json).contains("\"orderId\":\"100\"");
        assertThat(json).contains("\"userId\":\"1\"");
        assertThat(json).contains("\"quantity\":10");
        assertThat(json).contains("\"price\":50000.0");
    }

    @Test
    void sendOrderToMatcher_shouldThrowWhenRepositoryFails() {
        Long userId = 1L;
        OrderRequest request = createOrderRequest(5, 100.0, "SELL", "MARKET");
        when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("DB down"));
        when(jdbcTemplate.queryForMap(anyString(), eq(userId)))
                .thenReturn(Map.of("balance", new java.math.BigDecimal("1000.00"), "units", 10.0));

        assertThatThrownBy(() -> tradeService.sendOrderToMatcher(request, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");

        verifyNoInteractions(orderOutboxRepository);
    }

    // ── saveTrade ───────────────────────────────────────────────────────

    @Test
    void saveTrade_shouldSaveBothSidesAndUpdateWallets() {
        TradeConsumerDTO dto = createTradeConsumerDTO("10", "20", "100", "200", 50000.0, 5);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<Map> walletResponse = new ResponseEntity<>(Map.of("status", "ok"), HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(walletResponse);

        tradeService.saveTrade(dto);

        verify(tradeRepository, times(2)).save(tradeCaptor.capture());
        List<Trade> saved = tradeCaptor.getAllValues();
        assertThat(saved).hasSize(2);

        Trade buyerTrade = saved.get(0);
        assertThat(buyerTrade.getUserId()).isEqualTo(10L);
        assertThat(buyerTrade.getOrderId()).isEqualTo(100L);
        assertThat(buyerTrade.getPrice()).isEqualTo(50000.0);
        assertThat(buyerTrade.getQuantity()).isEqualTo(5);
        assertThat(buyerTrade.getSide()).isEqualTo("BUY");

        Trade sellerTrade = saved.get(1);
        assertThat(sellerTrade.getUserId()).isEqualTo(20L);
        assertThat(sellerTrade.getOrderId()).isEqualTo(200L);
        assertThat(sellerTrade.getPrice()).isEqualTo(50000.0);
        assertThat(sellerTrade.getQuantity()).isEqualTo(5);
        assertThat(sellerTrade.getSide()).isEqualTo("SELL");

        verify(restTemplate).exchange(
                eq("http://localhost:8081/internal/updateWallets"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class));
        verify(processedExecutionRepository).saveAndFlush(any(ProcessedExecution.class));
    }

    @Test
    void saveTrade_redeliveredExecutionDoesNotSettleAgain() {
        TradeConsumerDTO dto = createTradeConsumerDTO("10", "20", "100", "200", 100.0, 2);
        when(processedExecutionRepository.findById("execution-1"))
                .thenReturn(Optional.of(new ProcessedExecution(dto)));

        tradeService.saveTrade(dto);

        verifyNoInteractions(tradeRepository, restTemplate);
    }

    @Test
    void saveTrade_reusedIdWithDifferentPayloadIsRejected() {
        TradeConsumerDTO original = createTradeConsumerDTO("10", "20", "100", "200", 100.0, 2);
        TradeConsumerDTO altered = createTradeConsumerDTO("10", "20", "100", "200", 100.0, 3);
        when(processedExecutionRepository.findById("execution-1"))
                .thenReturn(Optional.of(new ProcessedExecution(original)));

        assertThatThrownBy(() -> tradeService.saveTrade(altered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution ID reused");
        verifyNoInteractions(tradeRepository, restTemplate);
    }

    @Test
    void saveTrade_shouldThrowWhenWalletUpdateFails() {
        TradeConsumerDTO dto = createTradeConsumerDTO("10", "20", "100", "200", 50000.0, 5);
        when(tradeRepository.save(any(Trade.class))).thenAnswer(i -> i.getArgument(0));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> tradeService.saveTrade(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error updating wallets");
    }

    // ── updateWallets ───────────────────────────────────────────────────

    @Test
    void updateWallets_shouldPostToWalletService() {
        TradeConsumerDTO dto = createTradeConsumerDTO("10", "20", "100", "200", 50000.0, 5);
        ResponseEntity<Map> walletResponse = new ResponseEntity<>(Map.of("status", "ok"), HttpStatus.OK);
        when(restTemplate.exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(walletResponse);

        tradeService.updateWallets(dto);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8081/internal/updateWallets"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Map.class));
        @SuppressWarnings("unchecked")
        HttpEntity<TradeConsumerDTO> entity = entityCaptor.getValue();
        assertThat(entity.getBody()).isEqualTo(dto);
        assertThat(entity.getHeaders().get("X-Internal-Secret")).contains("testSecret");
    }

    @Test
    void updateWallets_shouldThrowWhenResponseNot2xx() {
        TradeConsumerDTO dto = createTradeConsumerDTO("10", "20", "100", "200", 50000.0, 5);
        ResponseEntity<Map> errorResponse = new ResponseEntity<>(Map.of("error", "fail"), HttpStatus.BAD_REQUEST);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(errorResponse);

        assertThatThrownBy(() -> tradeService.updateWallets(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to update wallet");
    }

    @Test
    void updateWallets_shouldThrowOnException() {
        TradeConsumerDTO dto = createTradeConsumerDTO("10", "20", "100", "200", 50000.0, 5);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> tradeService.updateWallets(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error updating wallets");
    }

    // ── pastTrades ──────────────────────────────────────────────────────

    @Test
    void pastTrades_shouldReturnSortedTrades() {
        Long userId = 1L;
        Timestamp t1 = Timestamp.from(Instant.parse("2025-01-02T00:00:00Z"));
        Timestamp t2 = Timestamp.from(Instant.parse("2025-01-01T00:00:00Z"));
        Trade trade1 = createTrade(1L, 100L, userId, 50000.0, 5, "BUY");
        trade1.setCreatedAt(t1);
        Trade trade2 = createTrade(2L, 101L, userId, 50100.0, 3, "SELL");
        trade2.setCreatedAt(t2);
        when(tradeRepository.findByUserId(userId)).thenReturn(List.of(trade1, trade2));

        List<PastTradeDTO> result = tradeService.pastTrades(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCreatedAt()).isEqualTo(t1);
        assertThat(result.get(0).getPrice()).isEqualTo(50000.0);
        assertThat(result.get(0).getQuantity()).isEqualTo(5);
        assertThat(result.get(0).getSide()).isEqualTo("BUY");
        assertThat(result.get(1).getCreatedAt()).isEqualTo(t2);
    }

    @Test
    void pastTrades_shouldReturnNullWhenNoTrades() {
        when(tradeRepository.findByUserId(anyLong())).thenReturn(List.of());
        assertThat(tradeService.pastTrades(1L)).isNull();
    }

    @Test
    void pastTrades_shouldReturnNullWhenNull() {
        when(tradeRepository.findByUserId(anyLong())).thenReturn(null);
        assertThat(tradeService.pastTrades(1L)).isNull();
    }

    // ── getWallet ───────────────────────────────────────────────────────

    @Test
    void getWallet_shouldFetchFromWalletService() {
        setupAuthContext("my.jwt.token", "fp123");

        WalletDTO wallet = new WalletDTO();
        wallet.setWalletId(1L);
        wallet.setUserId(1L);
        wallet.setBalance(100000.0);
        wallet.setUnits(50.0);
        ResponseEntity<WalletDTO> response = new ResponseEntity<>(wallet, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(WalletDTO.class)))
                .thenReturn(response);

        WalletDTO result = tradeService.getWallet();

        assertThat(result).isNotNull();
        assertThat(result.getBalance()).isEqualTo(100000.0);
        assertThat(result.getUnits()).isEqualTo(50.0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq("http://localhost:8081/getWallet"),
                eq(HttpMethod.GET),
                entityCaptor.capture(),
                eq(WalletDTO.class));
        HttpEntity<Void> entity = entityCaptor.getValue();
        assertThat(entity.getHeaders().get("Cookie")).contains("jwt=my.jwt.token");
        assertThat(entity.getHeaders().get("X-Device-Fingerprint")).contains("fp123");
    }

    @Test
    void getWallet_shouldReturnNullOnException() {
        setupAuthContext("my.jwt.token", "fp123");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(WalletDTO.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        WalletDTO result = tradeService.getWallet();
        assertThat(result).isNull();
    }

    // ── checkWallet ─────────────────────────────────────────────────────

    @Test
    void checkWallet_shouldCheckBuyMarketPriceCap() {
        setupAuthContext("jwt", "fp");
        mockGetWalletResponse(100.0, 10.0);

        boolean result = tradeService.checkWallet(createOrderRequest(5, 20.0, "BUY", "MARKET"));
        assertThat(result).isTrue();
    }

    @Test
    void checkWallet_shouldCheckBalanceForBuyLimit() {
        setupAuthContext("jwt", "fp");
        mockGetWalletResponse(1000.0, 10.0);

        boolean result = tradeService.checkWallet(createOrderRequest(5, 200.0, "BUY", "LIMIT"));
        assertThat(result).isTrue();
    }

    @Test
    void checkWallet_shouldReturnFalseWhenInsufficientBalanceForBuyLimit() {
        setupAuthContext("jwt", "fp");
        mockGetWalletResponse(500.0, 10.0);

        boolean result = tradeService.checkWallet(createOrderRequest(5, 200.0, "BUY", "LIMIT"));
        assertThat(result).isFalse();
    }

    @Test
    void checkWallet_shouldCheckUnitsForSell() {
        setupAuthContext("jwt", "fp");
        mockGetWalletResponse(1000.0, 10.0);

        boolean result = tradeService.checkWallet(createOrderRequest(5, 200.0, "SELL", "LIMIT"));
        assertThat(result).isTrue();
    }

    @Test
    void checkWallet_shouldReturnFalseWhenInsufficientUnitsForSell() {
        setupAuthContext("jwt", "fp");
        mockGetWalletResponse(1000.0, 3.0);

        boolean result = tradeService.checkWallet(createOrderRequest(5, 200.0, "SELL", "MARKET"));
        assertThat(result).isFalse();
    }

    @Test
    void checkWallet_shouldReturnFalseWhenWalletNull() {
        setupAuthContext("jwt", "fp");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(WalletDTO.class)))
                .thenThrow(new RuntimeException("fail"));

        boolean result = tradeService.checkWallet(createOrderRequest(5, 200.0, "BUY", "LIMIT"));
        assertThat(result).isFalse();
    }

    // ── getOrders ───────────────────────────────────────────────────────

    @Test
    void getOrders_shouldReturnSortedOrders() {
        Long userId = 1L;
        Timestamp t1 = Timestamp.from(Instant.parse("2025-01-02T00:00:00Z"));
        Timestamp t2 = Timestamp.from(Instant.parse("2025-01-01T00:00:00Z"));
        Order order1 = createOrder(100L, userId, 50000.0, 5, "BUY", "LIMIT");
        order1.setCreatedAt(t1);
        Order order2 = createOrder(101L, userId, 50100.0, 3, "SELL", "MARKET");
        order2.setCreatedAt(t2);
        when(orderRepository.findByUserId(userId)).thenReturn(List.of(order1, order2));

        List<OrderDTO> result = tradeService.getOrders(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrderId()).isEqualTo(100L);
        assertThat(result.get(0).getPrice()).isEqualTo(50000.0);
        assertThat(result.get(1).getOrderId()).isEqualTo(101L);
    }

    @Test
    void getOrders_shouldReturnNullWhenNoOrders() {
        when(orderRepository.findByUserId(anyLong())).thenReturn(List.of());
        assertThat(tradeService.getOrders(1L)).isNull();
    }

    @Test
    void getOrders_shouldReturnNullWhenNull() {
        when(orderRepository.findByUserId(anyLong())).thenReturn(null);
        assertThat(tradeService.getOrders(1L)).isNull();
    }

    // ── updateOrder (StatusConsumerDTO) ─────────────────────────────────

    @Test
    void updateOrder_marketOrderFullyFilled_shouldDeleteAndToast() {
        Long userId = 1L;
        Order order = createOrder(100L, userId, 50000.0, 5, "BUY", "MARKET");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        StatusConsumerDTO dto = createStatusDTO("100", "1", "BUY", 5);

        tradeService.updateOrder(dto);

        verify(orderRepository).delete(order);
        verify(messagingTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void updateOrder_marketOrderPartiallyFilled_shouldDeleteAndToast() {
        Long userId = 1L;
        Order order = createOrder(100L, userId, 50000.0, 5, "BUY", "MARKET");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        StatusConsumerDTO dto = createStatusDTO("100", "1", "BUY", 3);

        tradeService.updateOrder(dto);

        verify(orderRepository).delete(order);
        verify(messagingTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void updateOrder_marketOrderCancelled_shouldDeleteAndToast() {
        Long userId = 1L;
        Order order = createOrder(100L, userId, 50000.0, 5, "BUY", "MARKET");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        StatusConsumerDTO dto = createStatusDTO("100", "1", "BUY", 0);

        tradeService.updateOrder(dto);

        verify(orderRepository).delete(order);
        verify(messagingTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void updateOrder_limitOrderFullyFilled_shouldDeleteAndToast() {
        Long userId = 1L;
        Order order = createOrder(100L, userId, 50000.0, 5, "BUY", "LIMIT");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        StatusConsumerDTO dto = createStatusDTO("100", "1", "BUY", 5);

        tradeService.updateOrder(dto);

        verify(orderRepository).delete(order);
        verify(messagingTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void updateOrder_limitOrderPartiallyFilled_shouldReduceQuantityAndSave() {
        Long userId = 1L;
        Order order = createOrder(100L, userId, 50000.0, 5, "BUY", "LIMIT");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        StatusConsumerDTO dto = createStatusDTO("100", "1", "BUY", 3);

        tradeService.updateOrder(dto);

        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getQuantity()).isEqualTo(2);
        verify(messagingTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void updateOrder_shouldIgnoreWhenOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());
        StatusConsumerDTO dto = createStatusDTO("999", "1", "BUY", 5);

        tradeService.updateOrder(dto);
        verify(orderRepository, never()).delete(any());
        verify(orderRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void updateOrder_shouldThrowWhenQuantityExceedsForLimit() {
        Order order = createOrder(100L, 1L, 50000.0, 5, "BUY", "LIMIT");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        StatusConsumerDTO dto = createStatusDTO("100", "1", "BUY", 10);

        assertThatThrownBy(() -> tradeService.updateOrder(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Quantity status cannot be greater");
    }
}
