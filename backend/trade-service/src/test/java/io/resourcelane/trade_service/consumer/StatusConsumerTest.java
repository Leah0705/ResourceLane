package io.resourcelane.trade_service.consumer;

import io.resourcelane.trade_service.dto.StatusConsumerDTO;
import io.resourcelane.trade_service.service.TradeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusConsumerTest {

    @Mock
    private TradeService tradeService;

    @InjectMocks
    private StatusConsumer statusConsumer;

    @Captor
    private ArgumentCaptor<StatusConsumerDTO> captor;

    @Test
    void listenTrade_shouldDeserializeAndCallUpdateOrder() {
        String json = "{\"orderId\":\"100\",\"userId\":\"1\",\"side\":\"BUY\",\"quantity\":5,\"timestamp\":1790747456218}";

        statusConsumer.listenTrade(json);

        verify(tradeService).updateOrder(captor.capture());
        StatusConsumerDTO dto = captor.getValue();
        assertThat(dto.getOrderId()).isEqualTo("100");
        assertThat(dto.getUserId()).isEqualTo("1");
        assertThat(dto.getSide()).isEqualTo("BUY");
        assertThat(dto.getQuantity()).isEqualTo(5);
    }

    @Test
    void listenTrade_shouldThrowOnInvalidJson() {
        String invalidJson = "{bad}";

        assertThatThrownBy(() -> statusConsumer.listenTrade(invalidJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process trade message");

        verify(tradeService, never()).updateOrder(any());
    }
}
