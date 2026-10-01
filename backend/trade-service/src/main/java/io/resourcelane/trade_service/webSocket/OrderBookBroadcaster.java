package io.resourcelane.trade_service.webSocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Broadcaster for Order Book updates.
 * Periodically sends the latest order book from the cache to WebSocket clients.
 */
@Component
public class OrderBookBroadcaster {
    /** Template for dispatching messages over WebSocket connections. */
    private final SimpMessagingTemplate messagingTemplate;

    /** Cache holding the latest order book data. */
    private final OrderBookCache orderBookCache;

    /**
     * Constructs the OrderBookBroadcaster.
     *
     * @param messagingTemplate The template for WebSocket messaging.
     * @param orderBookCache    The cache containing order book data.
     */
    public OrderBookBroadcaster(SimpMessagingTemplate messagingTemplate, OrderBookCache orderBookCache) {
        this.messagingTemplate = messagingTemplate;
        this.orderBookCache = orderBookCache;
    }

    /**
     * Broadcasts the latest order book to "/topic/orderbook" every 3 seconds.
     */
    @Scheduled(fixedRate = 3000)
    public void broadcastOrderBook() {
        String orderBook = orderBookCache.get();
        if (orderBook != null) {
            messagingTemplate.convertAndSend("/topic/orderbook", orderBook);
        }
    }
}