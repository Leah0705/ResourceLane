package io.resourcelane.trade_service.consumer;

import io.resourcelane.trade_service.dto.TradeConsumerDTO;
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
class TradeConsumerTest {

    @Mock
    private TradeService tradeService;

    @InjectMocks
    private TradeConsumer tradeConsumer;

    @Captor
    private ArgumentCaptor<TradeConsumerDTO> captor;

    @Test
    void listenTrade_shouldDeserializeAndCallSaveTrade() {
        String json = "{\"executionId\":\"exec-100\",\"buyUserId\":\"10\",\"sellUserId\":\"20\",\"buyOrderId\":\"100\",\"sellOrderId\":\"200\",\"price\":50000.0,\"quantity\":5,\"timestamp\":1790747456218}";

        tradeConsumer.listenTrade(json);

        verify(tradeService).saveTrade(captor.capture());
        TradeConsumerDTO dto = captor.getValue();
        assertThat(dto.getBuyUserId()).isEqualTo("10");
        assertThat(dto.getExecutionId()).isEqualTo("exec-100");
        assertThat(dto.getSellUserId()).isEqualTo("20");
        assertThat(dto.getBuyOrderId()).isEqualTo("100");
        assertThat(dto.getSellOrderId()).isEqualTo("200");
        assertThat(dto.getPrice()).isEqualTo(50000.0);
        assertThat(dto.getQuantity()).isEqualTo(5);
    }

    @Test
    void listenTrade_shouldThrowOnInvalidJson() {
        String invalidJson = "{bad json}";

        assertThatThrownBy(() -> tradeConsumer.listenTrade(invalidJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to process trade message");

        verify(tradeService, never()).saveTrade(any());
    }
}
