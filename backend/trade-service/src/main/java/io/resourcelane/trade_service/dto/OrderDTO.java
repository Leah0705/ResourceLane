package io.resourcelane.trade_service.dto;


import java.sql.Timestamp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object for Order details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {

    /**
     * Unique identifier for the order.
     */
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

}


