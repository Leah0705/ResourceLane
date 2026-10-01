package io.resourcelane.trade_service.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * DTO for incoming order requests from clients.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {

    /**
     * The quantity of the asset to trade. Must be positive.
     */
    @Positive(message = "Quantity must be greater than 0")
    private int quantity;

    /**
     * The price per unit. Required and must be positive.
     */
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Price must have at most two decimal places")
    private Double price;

    /**
     * The side of the trade (BUY or SELL).
     */
    @NotNull(message = "Side is required")
    @Pattern(regexp = "BUY|SELL", message = "Side must be either BUY or SELL")
    private String side;

    /**
     * The type of the order (LIMIT or MARKET).
     */
    @NotNull(message = "Type is required")
    @Pattern(regexp = "LIMIT|MARKET", message = "Type must be either LIMIT or MARKET")
    private String type;
}
