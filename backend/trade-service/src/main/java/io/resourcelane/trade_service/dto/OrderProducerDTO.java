package io.resourcelane.trade_service.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for sending order details to the Kafka producer.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderProducerDTO {
    /**
     * The unique identifier of the order.
     */
    private String orderId;

    /**
     * The identifier of the user placing the order.
     */
    private String userId;

    /**
     * The quantity of the asset.
     */
    private int quantity;

    /**
     * The price per unit (for LIMIT orders).
     */
    private Double price;

    /**
     * The side of the order (BUY or SELL).
     */
    private String side;

    /**
     * The type of the order (LIMIT or MARKET).
     */
    private String type;
}
