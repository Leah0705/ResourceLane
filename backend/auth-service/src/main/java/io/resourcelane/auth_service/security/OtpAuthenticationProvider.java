package io.resourcelane.auth_service.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import java.util.List;

import io.resourcelane.auth_service.model.User;
import io.resourcelane.auth_service.service.AuthService;

/**
 * Authentication provider for handling OTP/TOTP based authentication.
 */
@Component
public class OtpAuthenticationProvider implements AuthenticationProvider {

    /**
     * Service to handle authentication logic like retrieving users and verifying TOTP.
     */
    private final AuthService authService;

    /**
     * Constructs the authentication provider with the required AuthService.
     * 
     * @param authService The service used for core authentication operations.
     */
    public OtpAuthenticationProvider(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates the user based on phone number and TOTP.
     *
     * @param authentication The authentication request object.
     * @return A fully authenticated token if successful.
     * @throws AuthenticationException If authentication fails.
     */
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String phoneNumber = authentication.getPrincipal().toString();
        String totp = authentication.getCredentials().toString();

        User user = authService.getUserByPhone(phoneNumber);
        if (user == null) {
            throw new BadCredentialsException("User not found");
        }

        boolean valid = authService.verifyTotp(user.getSecretKey(), totp, user);
        if (!valid) {
            throw new BadCredentialsException("Invalid TOTP");
        }

        return new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    /**
     * Checks if this provider supports the given authentication type.
     *
     * @param authentication The authentication class.
     * @return True if supported, false otherwise.
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return OtpAuthenticationToken.class.isAssignableFrom(authentication);
    }
}