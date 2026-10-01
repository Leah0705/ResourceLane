package io.resourcelane.trade_service.webSocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderBookBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private OrderBookCache orderBookCache;

    @InjectMocks
    private OrderBookBroadcaster orderBookBroadcaster;

    @Test
    void broadcastOrderBook_shouldSendCachedOrderBook() {
        when(orderBookCache.get()).thenReturn("{\"bids\":[],\"asks\":[]}");

        orderBookBroadcaster.broadcastOrderBook();

        verify(messagingTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void broadcastOrderBook_shouldNotSendWhenCacheIsNull() {
        when(orderBookCache.get()).thenReturn(null);

        orderBookBroadcaster.broadcastOrderBook();

        verify(messagingTemplate, never()).convertAndSend(anyString(), anyString());
    }
}
