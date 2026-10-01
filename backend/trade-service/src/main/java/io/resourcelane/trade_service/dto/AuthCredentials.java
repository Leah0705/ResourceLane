package io.resourcelane.trade_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for authentication credentials.
 */
@Data   
@AllArgsConstructor
@NoArgsConstructor
public class AuthCredentials {
    /**
     * The JWT token.
     */
    private String jwt;

    /**
     * The device fingerprint.
     */
    private String fingerprint;
}
