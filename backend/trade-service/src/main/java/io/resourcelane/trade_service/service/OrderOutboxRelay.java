package io.resourcelane.trade_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries pending order events after broker outages or process restarts. */
@Component
public class OrderOutboxRelay {
    private static final Logger logger = LoggerFactory.getLogger(OrderOutboxRelay.class);
    private final OrderOutboxDispatcher dispatcher;

    public OrderOutboxRelay(OrderOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${order.outbox.poll-ms:500}")
    public void dispatchPending() {
        try {
            for (int sent = 0; sent < 100 && dispatcher.dispatchNext(); sent++) {
                // Each call commits its own acknowledged order before claiming the next.
            }
        } catch (Exception e) {
            logger.warn("Order outbox dispatch failed; pending event will be retried", e);
        }
    }
}
