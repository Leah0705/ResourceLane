package io.resourcelane.trade_service.dto;


import java.sql.Timestamp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO representing a past trade execution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PastTradeDTO {

    private Long tradeId;

    /**
     * The ID of the user involved in the trade.
     */
    private Long userId;  

    /**
     * The ID of the order associated with the trade.
     */
    private Long orderId;

    /**
     * The execution price.
     */
    private Double price;

    /**
     * The traded quantity.
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
}

