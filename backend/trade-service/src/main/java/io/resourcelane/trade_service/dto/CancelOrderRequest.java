package io.resourcelane.trade_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to cancel one of the caller's resting limit orders.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancelOrderRequest {

    /** ID of the order to cancel. */
    @NotNull(message = "Order ID is required")
    private Long orderId;
}
