package io.resourcelane.auth_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object for Registration Request.
 * Encapsulates the user details required to register a new account.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    /**
     * The username of the user.
     */
    @NotBlank(message = "Username is required")
    private String userName;

    /**
     * The phone number of the user. Must be 10 digits.
     */
    @NotBlank(message = "Demo account ID is required")
    @Pattern(regexp = "\\d{10}", message = "Demo account ID must be 10 digits")
    private String phoneNumber;
}
