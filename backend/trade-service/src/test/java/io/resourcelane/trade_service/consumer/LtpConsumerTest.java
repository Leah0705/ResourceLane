package io.resourcelane.trade_service.consumer;

import io.resourcelane.trade_service.webSocket.LtpCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LtpConsumerTest {

    @Mock
    private LtpCache ltpCache;

    @InjectMocks
    private LtpConsumer ltpConsumer;

    @Test
    void listenLtp_shouldStoreMessageInCache() {
        String message = "{\"price\":50000.0}";

        ltpConsumer.listenLtp(message);

        verify(ltpCache).set(message);
    }

    @Test
    void listenLtp_shouldStoreEmptyMessage() {
        ltpConsumer.listenLtp("");
        verify(ltpCache).set("");
    }
}
