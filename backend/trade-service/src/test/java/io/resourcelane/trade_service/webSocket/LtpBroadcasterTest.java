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
class LtpBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private LtpCache ltpCache;

    @InjectMocks
    private LtpBroadcaster ltpBroadcaster;

    @Test
    void broadcastLtp_shouldSendCachedLtp() {
        when(ltpCache.get()).thenReturn("{\"price\":50500.0}");

        ltpBroadcaster.broadcastLtp();

        verify(messagingTemplate).convertAndSend(anyString(), anyString());
    }

    @Test
    void broadcastLtp_shouldNotSendWhenCacheIsNull() {
        when(ltpCache.get()).thenReturn(null);

        ltpBroadcaster.broadcastLtp();

        verify(messagingTemplate, never()).convertAndSend(anyString(), anyString());
    }
}
