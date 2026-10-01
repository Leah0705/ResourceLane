package io.resourcelane.wallet_service.dto;


import lombok.Data;
import lombok.NoArgsConstructor;


import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;

/**
 * Data Transfer Object (DTO) for client requests to deposit demo credits into their wallet.
 * Includes necessary validation checks to guarantee the deposit amount is 
 * not null and strictly greater than zero, preventing invalid or zero-value transactions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMoneyRequest {

    /**
     * The exact monetary value the user intends to deposit.
     * Validated at the controller level to ensure it is a positive amount.
     */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private Double amount;
}
