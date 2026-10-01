package io.resourcelane.trade_service.consumer;

import io.resourcelane.trade_service.webSocket.OrderBookCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderBookConsumerTest {

    @Mock
    private OrderBookCache orderBookCache;

    @InjectMocks
    private OrderBookConsumer orderBookConsumer;

    @Test
    void listenOrderBook_shouldStoreMessageInCache() {
        String message = "{\"bids\":[],\"asks\":[]}";

        orderBookConsumer.listenOrderBook(message);

        verify(orderBookCache).set(message);
    }

    @Test
    void listenOrderBook_shouldStoreEmptyMessage() {
        orderBookConsumer.listenOrderBook("");
        verify(orderBookCache).set("");
    }
}
