package io.resourcelane.auth_service.dto;


import lombok.Data;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for User information.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    /**
     * The unique identifier of the user. Ignored in JSON output.
     */
    @JsonIgnore
    private Long userId;
    
    /**
     * The username of the user.
     */
    private String userName;

    /**
     * The phone number of the user.
     */
    private String phoneNumber;

    /**
     * The secret key for TOTP. Ignored in JSON output.
     */
    @JsonIgnore
    private String secretKey;
    
    /**
     * The state of the user (e.g., temporary, permanent). Ignored in JSON output.
     */
    @JsonIgnore
    private String state;

}
