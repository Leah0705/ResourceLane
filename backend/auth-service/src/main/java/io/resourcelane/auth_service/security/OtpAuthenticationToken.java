package io.resourcelane.auth_service.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Custom Authentication Token for OTP-based authentication.
 */
public class OtpAuthenticationToken extends AbstractAuthenticationToken {
    /**
     * The phone number identifying the user attempting authentication.
     */
    private final String phoneNumber;

    /**
     * The Time-Based One-Time Password provided by the user.
     */
    private final String totp;

    /**
     * Constructs an OtpAuthenticationToken.
     *
     * @param phoneNumber The user's phone number.
     * @param totp        The TOTP code.
     */
    public OtpAuthenticationToken(String phoneNumber, String totp) {
        super(null);
        this.phoneNumber = phoneNumber;
        this.totp = totp;
        setAuthenticated(false);
    }

    /**
     * Retrieves the user's phone number.
     * 
     * @return The phone number string.
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Retrieves the TOTP code.
     * 
     * @return The TOTP string.
     */
    public String getTotp() {
        return totp;
    }

    /**
     * Retrieves the credentials for this authentication token, which is the TOTP code.
     * 
     * @return The TOTP code.
     */
    @Override
    public Object getCredentials() {
        return totp;
    }

    /**
     * Retrieves the principal for this authentication token, which is the phone number.
     * 
     * @return The phone number.
     */
    @Override
    public Object getPrincipal() {
        return phoneNumber;
    }
}