package io.resourcelane.trade_service.service;

import io.resourcelane.trade_service.dto.OrderRequest;
import io.resourcelane.trade_service.dto.PastTradeDTO;
import io.resourcelane.trade_service.dto.TradeConsumerDTO;
import io.resourcelane.trade_service.dto.StatusConsumerDTO;
import io.resourcelane.trade_service.model.OrderOutboxEvent;
import io.resourcelane.trade_service.repository.OrderOutboxRepository;
import io.resourcelane.trade_service.repository.OrderReservationRepository;
import io.resourcelane.trade_service.repository.OrderRepository;
import io.resourcelane.trade_service.repository.ProcessedExecutionRepository;
import io.resourcelane.trade_service.repository.TradeRepository;
import java.util.Map;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "order.outbox.poll-ms=600000")
class TradeReliabilityIntegrationTest {
    @Autowired private TradeService tradeService;
    @Autowired private OrderRepository orders;
    @Autowired private OrderOutboxRepository outbox;
    @Autowired private OrderReservationRepository reservations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TradeRepository trades;
    @Autowired private ProcessedExecutionRepository processed;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockitoBean private RestTemplate walletHttp;

    @BeforeEach
    void reset() {
        processed.deleteAll();
        trades.deleteAll();
        outbox.deleteAll();
        reservations.deleteAll();
        orders.deleteAll();
        jdbc.execute("create table if not exists wallets (user_id bigint primary key, balance decimal(19,2), units double precision)");
        jdbc.update("delete from wallets");
        jdbc.update("insert into wallets (user_id, balance, units) values (?, ?, ?)", 7L, new java.math.BigDecimal("100.00"), 10.0);
    }

    @Test
    void committedOrderAlwaysHasAnOutboxEvent() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(2, 10.25, "BUY", "LIMIT"), 7L);

        assertThat(orders.count()).isEqualTo(1);
        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getOrderId()).isEqualTo(orders.findAll().get(0).getOrderId());
            assertThat(event.getPublishedAt()).isNull();
            assertThat(event.getPayload()).contains("\"price\":10.25");
        });
        assertThat(reservations.findAll()).singleElement().satisfies(hold -> {
            assertThat(hold.getOrderId()).isEqualTo(orders.findAll().get(0).getOrderId());
            assertThat(hold.heldQuantity()).isEqualTo(2);
        });
        List<OrderOutboxEvent> pending = transactionTemplate.execute(
                status -> outbox.findPending(PageRequest.of(0, 1)));
        assertThat(pending).hasSize(1);
    }

    @Test
    void secondOrderCannotReuseReservedCredits() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(6, 10.25, "BUY", "LIMIT"), 7L);

        assertThatThrownBy(() -> tradeService.sendOrderToMatcher(
                new OrderRequest(6, 10.25, "BUY", "LIMIT"), 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient available balance");
        assertThat(orders.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(reservations.count()).isEqualTo(1);
    }

    @Test
    void crossingOwnOrderIsRejectedBeforePublishing() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(2, 10.25, "SELL", "LIMIT"), 7L);
        tradeService.sendOrderToMatcher(new OrderRequest(2, 10.00, "BUY", "LIMIT"), 7L);

        assertThatThrownBy(() -> tradeService.sendOrderToMatcher(
                new OrderRequest(2, 10.25, "BUY", "LIMIT"), 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own active order");
        assertThat(orders.count()).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(2);
    }

    @Test
    void marketSellCannotMatchOwnRestingBuy() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(2, 10.25, "BUY", "LIMIT"), 7L);

        assertThatThrownBy(() -> tradeService.sendOrderToMatcher(
                new OrderRequest(1, 10.25, "SELL", "MARKET"), 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("your own active order");
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void marketCompletionReleasesOnlyUnmatchedQuantity() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(5, 10.25, "BUY", "MARKET"), 7L);
        Long orderId = orders.findAll().get(0).getOrderId();
        StatusConsumerDTO completed = new StatusConsumerDTO(orderId.toString(), "7", "BUY", 2);
        completed.setRemainingQuantity(0);
        completed.setMatchedQuantity(2);
        completed.setCompleted(true);
        tradeService.updateOrder(completed);
        tradeService.updateOrder(completed);

        assertThat(orders.count()).isZero();
        assertThat(reservations.findById(orderId).orElseThrow().heldQuantity()).isEqualTo(2);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void repeatedAndOutOfOrderStatusesCannotReduceALimitOrderTwice() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(5, 10.25, "BUY", "LIMIT"), 7L);
        Long orderId = orders.findAll().get(0).getOrderId();
        StatusConsumerDTO newer = new StatusConsumerDTO(orderId.toString(), "7", "BUY", 2);
        newer.setRemainingQuantity(3);
        tradeService.updateOrder(newer);
        tradeService.updateOrder(newer);
        StatusConsumerDTO older = new StatusConsumerDTO(orderId.toString(), "7", "BUY", 1);
        older.setRemainingQuantity(4);
        tradeService.updateOrder(older);

        assertThat(orders.findById(orderId).orElseThrow().getQuantity()).isEqualTo(3);
        assertThat(reservations.findById(orderId).orElseThrow().heldQuantity()).isEqualTo(5);
    }

    @Test
    void cancelIsPublishedOnlyAfterItsOrder() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(2, 10.25, "BUY", "LIMIT"), 7L);
        Long orderId = orders.findAll().get(0).getOrderId();
        tradeService.requestCancel(orderId, 7L);

        List<OrderOutboxEvent> beforeOrderIsPublished = transactionTemplate.execute(
                status -> outbox.findPendingCancels(PageRequest.of(0, 1)));
        assertThat(beforeOrderIsPublished).isEmpty();

        transactionTemplate.executeWithoutResult(status -> {
            OrderOutboxEvent event = outbox.findById(orderId).orElseThrow();
            event.markPublished();
            outbox.save(event);
        });
        List<OrderOutboxEvent> cancels = transactionTemplate.execute(
                status -> outbox.findPendingCancels(PageRequest.of(0, 1)));
        assertThat(cancels).singleElement().satisfies(event ->
                assertThat(event.getCancelPayload()).contains("\"action\":\"CANCEL\"")
                        .contains("\"orderId\":\"" + orderId + "\""));
        assertThat(tradeService.getOrders(7L)).singleElement()
                .satisfies(order -> assertThat(order.isCancelRequested()).isTrue());
    }

    @Test
    void cancellationReleasesOnlyTheUnfilledReservation() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(5, 10.25, "BUY", "LIMIT"), 7L);
        Long orderId = orders.findAll().get(0).getOrderId();
        StatusConsumerDTO partial = new StatusConsumerDTO(orderId.toString(), "7", "BUY", 2);
        partial.setRemainingQuantity(3);
        tradeService.updateOrder(partial);

        // 51.25 is still held, so this order does not fit in the 100.00 balance yet.
        assertThatThrownBy(() -> tradeService.sendOrderToMatcher(new OrderRequest(7, 10.25, "BUY", "LIMIT"), 7L))
                .hasMessageContaining("Insufficient available balance");

        tradeService.requestCancel(orderId, 7L);
        StatusConsumerDTO cancelled = new StatusConsumerDTO(orderId.toString(), "7", "BUY", 0);
        cancelled.setRemainingQuantity(0);
        cancelled.setCompleted(true);
        cancelled.setCancelled(true);
        cancelled.setCancelledQuantity(3);
        tradeService.updateOrder(cancelled);
        tradeService.updateOrder(cancelled);

        assertThat(orders.findById(orderId)).isEmpty();
        // The two filled units stay held until their trade settles.
        assertThat(reservations.findById(orderId).orElseThrow().heldQuantity()).isEqualTo(2);
        tradeService.sendOrderToMatcher(new OrderRequest(7, 10.25, "BUY", "LIMIT"), 7L);
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test
    void cancelOfAnAlreadyFilledOrderReleasesNothing() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(2, 10.25, "BUY", "LIMIT"), 7L);
        Long orderId = orders.findAll().get(0).getOrderId();
        tradeService.requestCancel(orderId, 7L);
        StatusConsumerDTO nothingLeft = new StatusConsumerDTO(orderId.toString(), "7", "", 0);
        nothingLeft.setCancelled(true);
        nothingLeft.setCancelledQuantity(0);
        tradeService.updateOrder(nothingLeft);

        assertThat(reservations.findById(orderId).orElseThrow().heldQuantity()).isEqualTo(2);
    }

    @Test
    void invalidCancelRequestsAreRejected() throws Exception {
        tradeService.sendOrderToMatcher(new OrderRequest(2, 10.25, "BUY", "LIMIT"), 7L);
        Long limitId = orders.findAll().get(0).getOrderId();
        tradeService.requestCancel(limitId, 7L);

        assertThatThrownBy(() -> tradeService.requestCancel(limitId, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already requested");
        assertThatThrownBy(() -> tradeService.requestCancel(limitId, 8L))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> tradeService.requestCancel(999_999L, 7L))
                .isInstanceOf(NoSuchElementException.class);

        tradeService.sendOrderToMatcher(new OrderRequest(1, 10.25, "BUY", "MARKET"), 7L);
        Long marketId = orders.findAll().stream().map(o -> o.getOrderId())
                .filter(id -> !id.equals(limitId)).findFirst().orElseThrow();
        assertThatThrownBy(() -> tradeService.requestCancel(marketId, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit orders");
    }

    @Test
    void walletResponseTimeoutRollsBackHistoryAndRetryUsesSameExecutionId() {
        TradeConsumerDTO execution = new TradeConsumerDTO();
        execution.setExecutionId("integration-timeout-1");
        execution.setBuyOrderId("100");
        execution.setSellOrderId("200");
        execution.setBuyUserId("10");
        execution.setSellUserId("20");
        execution.setPrice(10.25);
        execution.setQuantity(2);
        when(walletHttp.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("response timeout"))
                .thenReturn(new ResponseEntity<>(Map.of("status", "ok"), HttpStatus.OK));

        assertThatThrownBy(() -> tradeService.saveTrade(execution))
                .hasRootCauseMessage("response timeout");
        assertThat(trades.count()).isZero();
        assertThat(processed.count()).isZero();

        tradeService.saveTrade(execution);
        assertThat(trades.count()).isEqualTo(2);
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test
    void partialFillsKeepTheirOwnPricesInTradeHistory() {
        TradeConsumerDTO first = new TradeConsumerDTO();
        first.setExecutionId("partial-1");
        first.setBuyOrderId("100");
        first.setSellOrderId("200");
        first.setBuyUserId("10");
        first.setSellUserId("20");
        first.setPrice(10.25);
        first.setQuantity(2);

        TradeConsumerDTO second = new TradeConsumerDTO();
        second.setExecutionId("partial-2");
        second.setBuyOrderId("100");
        second.setSellOrderId("201");
        second.setBuyUserId("10");
        second.setSellUserId("21");
        second.setPrice(9.75);
        second.setQuantity(1);
        when(walletHttp.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("status", "ok"), HttpStatus.OK));

        tradeService.saveTrade(first);
        tradeService.saveTrade(second);

        List<PastTradeDTO> history = tradeService.pastTrades(10L);
        assertThat(history).hasSize(2);
        assertThat(history).extracting(PastTradeDTO::getPrice, PastTradeDTO::getQuantity)
                .containsExactlyInAnyOrder(tuple(10.25, 2), tuple(9.75, 1));
        assertThat(history).extracting(PastTradeDTO::getOrderId).containsOnly(100L);
        assertThat(history).extracting(PastTradeDTO::getTradeId).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(trades.count()).isEqualTo(4);
    }
}
