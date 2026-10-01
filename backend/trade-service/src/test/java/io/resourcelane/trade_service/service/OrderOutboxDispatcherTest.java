package io.resourcelane.trade_service.service;

import io.resourcelane.trade_service.model.OrderOutboxEvent;
import io.resourcelane.trade_service.producer.OrderProducer;
import io.resourcelane.trade_service.repository.OrderOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderOutboxDispatcherTest {
    @Mock
    private OrderOutboxRepository outbox;
    @Mock
    private OrderProducer producer;
    @InjectMocks
    private OrderOutboxDispatcher dispatcher;

    @Test
    void marksPublishedOnlyAfterKafkaAck() throws Exception {
        OrderOutboxEvent event = new OrderOutboxEvent(123L, "{\"orderId\":\"123\"}");
        when(outbox.findPending(any(Pageable.class))).thenReturn(List.of(event));
        when(producer.sendOrder(event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        assertThat(dispatcher.dispatchNext()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outbox).save(event);
    }

    @Test
    void keepsFailedKafkaSendPendingForRetry() {
        OrderOutboxEvent event = new OrderOutboxEvent(123L, "{\"orderId\":\"123\"}");
        CompletableFuture<SendResult<String, String>> failure = new CompletableFuture<>();
        failure.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(outbox.findPending(any(Pageable.class))).thenReturn(List.of(event));
        when(producer.sendOrder(event.getPayload())).thenReturn(failure);

        assertThatThrownBy(() -> dispatcher.dispatchNext())
                .hasRootCauseMessage("broker unavailable");
        assertThat(event.getPublishedAt()).isNull();
        verify(outbox, never()).save(event);
    }
}
