package io.resourcelane.trade_service.service;

import io.resourcelane.trade_service.model.OrderOutboxEvent;
import io.resourcelane.trade_service.producer.OrderProducer;
import io.resourcelane.trade_service.repository.OrderOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Publishes one pending order and records success only after Kafka acknowledges it. */
@Service
public class OrderOutboxDispatcher {
    private final OrderOutboxRepository outbox;
    private final OrderProducer producer;

    public OrderOutboxDispatcher(OrderOutboxRepository outbox, OrderProducer producer) {
        this.outbox = outbox;
        this.producer = producer;
    }

    @Transactional
    public boolean dispatchNext() throws Exception {
        List<OrderOutboxEvent> pending = outbox.findPending(PageRequest.of(0, 1));
        if (pending.isEmpty()) {
            return false;
        }
        OrderOutboxEvent event = pending.get(0);
        producer.sendOrder(event.getPayload()).get(5, TimeUnit.SECONDS);
        event.markPublished();
        outbox.save(event);
        return true;
    }
}
