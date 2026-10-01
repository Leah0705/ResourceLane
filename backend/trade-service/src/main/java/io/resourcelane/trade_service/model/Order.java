package io.resourcelane.trade_service.model;


import java.sql.Timestamp;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Entity representing an Order in the system.
 * Maps to the "orders" table in the database.
 */
@Entity
@Getter @Setter @ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orders")
public class Order {

    /**
     * Unique identifier for the order.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;               

    /**
     * Identifier of the user who placed the order.
     */
    private Long userId;  

    /**
     * Price per unit of the order.
     */
    private Double price;

    /**
     * Quantity of the asset.
     */
    private int quantity;

    /**
     * Side of the order (BUY or SELL).
     */
    private String side;

    /**
     * Type of the order (MARKET or LIMIT).
     */
    private String type;

    /**
     * Timestamp when the order was created.
     */
    private Timestamp createdAt;

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


