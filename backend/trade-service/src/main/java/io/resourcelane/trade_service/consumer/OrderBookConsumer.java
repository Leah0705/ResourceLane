package io.resourcelane.trade_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
// import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import io.resourcelane.trade_service.webSocket.OrderBookCache;

/**
 * Kafka Consumer for Order Book updates.
 */
@Component
public class OrderBookConsumer {
    /** Cache component for storing the most recent order book state. */
    private final OrderBookCache orderBookCache;

    /**
     * Constructs the OrderBookConsumer.
     *
     * @param orderBookCache The cache to store the latest order book.
     */
    public OrderBookConsumer(OrderBookCache orderBookCache) {
        this.orderBookCache = orderBookCache;
    }

    /**
     * Listens for Order Book updates from Kafka.
     *
     * @param message The order book message in JSON format.
     */
    @KafkaListener(topics = "orderbook", containerFactory = "marketDataListenerFactory")
    public void listenOrderBook(String message) {
        orderBookCache.set(message);
    }
}