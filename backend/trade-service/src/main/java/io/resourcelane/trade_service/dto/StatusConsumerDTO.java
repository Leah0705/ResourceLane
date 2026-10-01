
package io.resourcelane.trade_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for consuming order status updates from Kafka.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusConsumerDTO {

    /**
     * The unique identifier of the order.
     */
    private String orderId;

    /**
     * The identifier of the user who placed the order.
     */
    private String userId;

    /**
     * The side of the order (buyer/seller).
     */
    private String side;

    /**
     * The remaining or affected quantity.
     */
    private int quantity; 
    private Integer remainingQuantity;
    private Integer matchedQuantity;
    private Boolean completed;
    /** True when this status answers a cancel request. */
    private Boolean cancelled;
    /** Quantity the matcher removed from the book; 0 when nothing was left to cancel. */
    private Integer cancelledQuantity;

    public StatusConsumerDTO(String orderId, String userId, String side, int quantity) {
        this.orderId = orderId;
        this.userId = userId;
        this.side = side;
        this.quantity = quantity;
    }
   
}
