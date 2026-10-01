package io.resourcelane.wallet_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for client requests to withdraw units from their wallet.
 * Employs validation annotations to enforce that the withdrawal quantity requested 
 * is a valid, strictly positive amount before hitting business logic.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawUnitsRequestDTO {
    /**
     * The number of units the user is attempting to withdraw.
     * Must be provided in the request body as a positive integer.
     */
    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than 0")
    // @JsonDeserialize(using = NumberDeserializers.IntegerDeserializer.class) //
    // Ensures only integer values are accepted
    private Integer quantity;
}
