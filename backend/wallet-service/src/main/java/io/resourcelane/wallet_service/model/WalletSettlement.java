package io.resourcelane.wallet_service.model;

import io.resourcelane.wallet_service.dto.TradeDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.math.BigDecimal;

/** One committed wallet transfer per matching-engine execution. */
@Entity
@Table(name = "wallet_settlements")
public class WalletSettlement {
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
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    private int quantity;

    protected WalletSettlement() {
    }

    public WalletSettlement(TradeDTO trade) {
        executionId = trade.getExecutionId();
        buyUserId = trade.getBuyUserId();
        sellUserId = trade.getSellUserId();
        buyOrderId = trade.getBuyOrderId();
        sellOrderId = trade.getSellOrderId();
        price = BigDecimal.valueOf(trade.getPrice()).setScale(2);
        quantity = trade.getQuantity();
    }

    public boolean matches(TradeDTO trade) {
        return Objects.equals(buyUserId, trade.getBuyUserId())
                && Objects.equals(sellUserId, trade.getSellUserId())
                && Objects.equals(buyOrderId, trade.getBuyOrderId())
                && Objects.equals(sellOrderId, trade.getSellOrderId())
                && price.compareTo(BigDecimal.valueOf(trade.getPrice())) == 0
                && quantity == trade.getQuantity();
    }
}
