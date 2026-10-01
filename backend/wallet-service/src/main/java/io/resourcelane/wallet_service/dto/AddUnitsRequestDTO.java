package io.resourcelane.wallet_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;


import jakarta.validation.constraints.Positive;


import jakarta.validation.constraints.NotNull;


import lombok.AllArgsConstructor;

/**
 * Data Transfer Object (DTO) for client requests to deposit units into their wallet.
 * Incorporates validation annotations to verify that the incoming payload contains 
 * a strictly positive quantity of units.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddUnitsRequestDTO {
    /**
     * The number of units the user wants to add to their demo wallet.
     * Validated to ensure it is not null and is greater than zero.
     */
    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than 0")
    // @JsonDeserialize(using = NumberDeserializers.IntegerDeserializer.class) // Ensures only integer values are accepted
    private Integer quantity;
}
