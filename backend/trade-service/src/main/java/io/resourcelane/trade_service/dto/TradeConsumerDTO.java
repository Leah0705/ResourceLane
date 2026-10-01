package io.resourcelane.trade_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for consuming trade execution details from Kafka.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeConsumerDTO {

    /** Unique ID of this individual match, preserved across Kafka redelivery. */
    private String executionId;

    /**
     * The ID of the buyer.
     */
    private String buyUserId;

    /**
     * The ID of the seller.
     */
    private String sellUserId;

    /**
     * The ID of the buy order.
     */
    private String buyOrderId;

    /**
     * The ID of the sell order.
     */
    private String sellOrderId;

    /**
     * The execution price.
     */
    private Double price;

    /**
     * The executed quantity.
     */
    private int quantity;

}
