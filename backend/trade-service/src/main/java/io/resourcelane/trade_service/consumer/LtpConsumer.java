package io.resourcelane.trade_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
// import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import io.resourcelane.trade_service.webSocket.LtpCache;

/**
 * Kafka Consumer for Last Traded Price (LTP) updates.
 */
@Component
public class LtpConsumer {

    /** Cache component for storing the most recently traded price. */
    private final LtpCache ltpCache;

    /**
     * Constructs the LtpConsumer.
     *
     * @param ltpCache The cache to store the latest LTP.
     */
    public LtpConsumer(LtpCache ltpCache) {
        this.ltpCache = ltpCache;
    }

    /**
     * Listens for LTP updates from Kafka.
     *
     * @param message The LTP message in JSON format.
     */
    @KafkaListener(topics = "ltp", containerFactory = "marketDataListenerFactory")
    public void listenLtp(String message) {
        ltpCache.set(message); // Just store
    }
}