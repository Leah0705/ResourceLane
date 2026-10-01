package io.resourcelane.trade_service.model;

import io.resourcelane.trade_service.dto.TradeConsumerDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/** Trade history already committed for one matching-engine execution. */
@Entity
@Table(name = "processed_executions")
public class ProcessedExecution {
    @Id
    @Column(length = 128)
    private String executionId;
    @Column(nullable = false)
    private String buyUserId;
    @Column(nullable = false)
    private String sellUserId;
    @Column(nullable = false)
    private String buyOrderId;
    @Column(nullable = false)
    private String sellOrderId;
    @Column(nullable = false)
    private Double price;
    private int quantity;

    protected ProcessedExecution() {
    }

    public ProcessedExecution(TradeConsumerDTO trade) {
        executionId = trade.getExecutionId();
        buyUserId = trade.getBuyUserId();
        sellUserId = trade.getSellUserId();
        buyOrderId = trade.getBuyOrderId();
        sellOrderId = trade.getSellOrderId();
        price = trade.getPrice();
        quantity = trade.getQuantity();
    }

    public boolean matches(TradeConsumerDTO trade) {
        return Objects.equals(buyUserId, trade.getBuyUserId())
                && Objects.equals(sellUserId, trade.getSellUserId())
                && Objects.equals(buyOrderId, trade.getBuyOrderId())
                && Objects.equals(sellOrderId, trade.getSellOrderId())
                && Objects.equals(price, trade.getPrice())
                && quantity == trade.getQuantity();
    }
}
