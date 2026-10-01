package io.resourcelane.auth_service.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.resourcelane.auth_service.model.User;
import io.resourcelane.auth_service.service.AuthService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class OtpAuthenticationProviderTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private OtpAuthenticationProvider provider;

    @Test
    void authenticate_validTotp_returnsAuthenticationToken() {
        OtpAuthenticationToken authRequest = new OtpAuthenticationToken("1234567890", "123456");
        User user = new User(1L, "testuser", "1234567890", "secretKey", "permanent");

        when(authService.getUserByPhone("1234567890")).thenReturn(user);
        when(authService.verifyTotp("secretKey", "123456", user)).thenReturn(true);

        Authentication result = provider.authenticate(authRequest);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(result.getPrincipal()).isSameAs(user);
        assertThat(result.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    void authenticate_userNotFound_throwsBadCredentialsException() {
        OtpAuthenticationToken authRequest = new OtpAuthenticationToken("1234567890", "123456");

        when(authService.getUserByPhone("1234567890")).thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(authRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("User not found");
    }

    @Test
    void authenticate_whenVerifyTotpThrowsException_propagates() {
        OtpAuthenticationToken authRequest = new OtpAuthenticationToken("1234567890", "123456");
        User user = new User(1L, "testuser", "1234567890", "secretKey", "temporary");

        when(authService.getUserByPhone("1234567890")).thenReturn(user);
        when(authService.verifyTotp("secretKey", "123456", user))
                .thenThrow(new RuntimeException("Failed to verify TOTP"));

        assertThatThrownBy(() -> provider.authenticate(authRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to verify TOTP");
    }

    @Test
    void supports_correctType_returnsTrue() {
        boolean result = provider.supports(OtpAuthenticationToken.class);

        assertThat(result).isTrue();
    }

    @Test
    void supports_wrongType_returnsFalse() {
        boolean result = provider.supports(UsernamePasswordAuthenticationToken.class);

        assertThat(result).isFalse();
    }

    @Test
    void supports_subtype_returnsTrue() {
        boolean result = provider.supports(OtpAuthenticationToken.class);

        assertThat(result).isTrue();
    }
}
