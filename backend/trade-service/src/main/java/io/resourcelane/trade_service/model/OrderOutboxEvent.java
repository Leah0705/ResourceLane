package io.resourcelane.trade_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Order event committed in the same database transaction as its order. */
@Entity
@Table(name = "order_outbox")
public class OrderOutboxEvent {
    @Id
    private Long orderId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    private Instant publishedAt;

    /**
     * Cancel message for this order, set when the user asks to cancel. It shares the row with the
     * order so it can only be published after the order itself.
     */
    @Column(columnDefinition = "text")
    private String cancelPayload;

    private Instant cancelPublishedAt;

    protected OrderOutboxEvent() {
    }

    public OrderOutboxEvent(Long orderId, String payload) {
        this.orderId = orderId;
        this.payload = payload;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void markPublished() {
        publishedAt = Instant.now();
    }

    public String getCancelPayload() {
        return cancelPayload;
    }

    public Instant getCancelPublishedAt() {
        return cancelPublishedAt;
    }

    public boolean isCancelRequested() {
        return cancelPayload != null;
    }

    public void requestCancel(String payload) {
        if (cancelPayload != null) {
            throw new IllegalStateException("Cancel already requested");
        }
        cancelPayload = payload;
    }

    public void markCancelPublished() {
        cancelPublishedAt = Instant.now();
    }
}
