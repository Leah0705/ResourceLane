package io.resourcelane.auth_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Login Request.
 * Contains the necessary information to initiate a user login.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    /**
     * The user's phone number. Must be 10 digits.
     */
    @NotBlank(message = "Demo account ID is required")
    @Pattern(regexp = "\\d{10}", message = "Demo account ID must be 10 digits")
    private String phoneNumber;

}
