package io.resourcelane.auth_service.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private OtpAuthenticationProvider otpAuthenticationProvider;

    @InjectMocks
    private SecurityConfig securityConfig;

    @Test
    void corsConfigurationSource_isCreated() throws Exception {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) securityConfig.corsConfigurationSource();
        assertThat(source).isNotNull();
    }

    @Test
    void authenticationManager_shouldBeCreated() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_DEEP_STUBS);
        AuthenticationManagerBuilder builder = mock(AuthenticationManagerBuilder.class);

        when(http.getSharedObject(AuthenticationManagerBuilder.class)).thenReturn(builder);
        when(builder.build()).thenReturn(mock(AuthenticationManager.class));

        AuthenticationManager manager = securityConfig.authenticationManager(http);

        assertThat(manager).isNotNull();
        verify(builder).authenticationProvider(otpAuthenticationProvider);
    }

    @Test
    void filterChain_shouldBeConfigured() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_DEEP_STUBS);

        when(http.cors(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.addFilterBefore(any(), any())).thenReturn(http);
        when(http.sessionManagement(any())).thenReturn(http);

        SecurityFilterChain chain = securityConfig.filterChain(http);

        assertThat(chain).isNotNull();
        verify(http).cors(any());
        verify(http).csrf(any());
        verify(http).authorizeHttpRequests(any());
        verify(http).addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        verify(http).sessionManagement(any());
    }
}
