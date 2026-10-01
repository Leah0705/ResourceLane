package io.resourcelane.trade_service.webSocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Broadcaster for Last Traded Price (LTP).
 * Periodically sends the latest LTP from the cache to WebSocket clients.
 */
@Component
public class LtpBroadcaster {
    /** Template for sending messages to connected WebSocket clients. */
    private final SimpMessagingTemplate messagingTemplate;

    /** Cache holding the latest traded price data. */
    private final LtpCache ltpCache;

    /**
     * Constructs the LtpBroadcaster.
     *
     * @param messagingTemplate The template for WebSocket messaging.
     * @param ltpCache          The cache containing LTP data.
     */
    public LtpBroadcaster(SimpMessagingTemplate messagingTemplate, LtpCache ltpCache) {
        this.messagingTemplate = messagingTemplate;
        this.ltpCache = ltpCache;
    }

    /**
     * Broadcasts the latest LTP to "/topic/ltp" every 3 seconds.
     */
    @Scheduled(fixedRate = 3000)
    public void broadcastLtp() {
        String ltp = ltpCache.get();
        if (ltp != null) {
            messagingTemplate.convertAndSend("/topic/ltp", ltp);
        }
    }
}