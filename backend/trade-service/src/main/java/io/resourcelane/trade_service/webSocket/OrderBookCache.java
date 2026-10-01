package io.resourcelane.trade_service.webSocket;

import org.springframework.stereotype.Component;

/**
 * Thread-safe cache for the latest Order Book snapshot.
 */
@Component
public class OrderBookCache {
    /** The most recently received snapshot of the order book. */
    private volatile String latestOrderBook;

    /**
     * Updates the latest order book.
     *
     * @param data The new order book JSON string.
     */
    public void set(String data) {
        this.latestOrderBook = data;
    }

    /**
     * Retrieves the latest order book.
     *
     * @return The latest order book JSON string.
     */
    public String get() {
        return latestOrderBook;
    }
}