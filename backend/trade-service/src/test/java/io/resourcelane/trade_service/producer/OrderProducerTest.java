package io.resourcelane.trade_service.producer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OrderProducer orderProducer;

    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Test
    void sendOrder_shouldSendToKafkaTopic() {
        String orderJson = "{\"orderId\":\"100\",\"userId\":\"1\",\"quantity\":5,\"price\":50000.0,\"side\":\"BUY\",\"type\":\"LIMIT\"}";
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        orderProducer.sendOrder(orderJson);

        verify(kafkaTemplate).send(topicCaptor.capture(), messageCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo("order");
        assertThat(messageCaptor.getValue()).isEqualTo(orderJson);
    }

    @Test
    void sendOrder_shouldHandleAsyncKafkaFailure() {
        String orderJson = "{\"orderId\":\"100\"}";
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString())).thenReturn(failedFuture);

        orderProducer.sendOrder(orderJson);

        verify(kafkaTemplate).send("order", orderJson);
    }

    @Test
    void sendOrder_shouldPropagateSyncException() {
        String orderJson = "{\"orderId\":\"100\"}";
        when(kafkaTemplate.send(anyString(), anyString())).thenThrow(new RuntimeException("Broker not available"));

        assertThatThrownBy(() -> orderProducer.sendOrder(orderJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Broker not available");
    }
}
