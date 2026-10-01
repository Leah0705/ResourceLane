package io.resourcelane.wallet_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;

/**
 * Data Transfer Object (DTO) for client requests to withdraw demo credits from their wallet.
 * Includes validation constraints to ensure the requested withdrawal amount is strictly positive
 * before the request is processed by the controller.
 */
@Data
@NoArgsConstructor  
@AllArgsConstructor
public class WithdrawMoneyRequest {

    /**
     * The exact monetary amount the user wishes to withdraw.
     * Must be a positive number greater than zero to be considered valid.
     */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than 0")
    private Double amount;  
}
