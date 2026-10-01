package io.resourcelane.trade_service.model;


import java.sql.Timestamp;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Entity representing a Trade execution.
 * Maps to the "trades" table in the database.
 */
@Entity
@Getter @Setter @ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "trades")
public class Trade {

    /**
     * Unique identifier for the trade record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tradeId;

    /**
     * The ID of the order associated with this trade.
     */
    private Long orderId;

    /**
     * The ID of the user involved in this trade.
     */
    private Long userId;  

    /**
     * The execution price.
     */
    private Double price;

    /**
     * The executed quantity.
     */
    private int quantity;

    /**
     * The timestamp of the trade.
     */
    private Timestamp createdAt;

    /**
     * The side of the trade (BUY or SELL).
     */
    private String side;

    /**
     * Sets the timestamp before persisting the entity if not already set.
     */
    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = new Timestamp(System.currentTimeMillis());
        }
    }

}


