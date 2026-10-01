package io.resourcelane.trade_service.Security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void corsConfigurationSource_shouldConfigureCors() {
        SecurityConfig config = new SecurityConfig(jwtAuthenticationFilter);

        var source = config.corsConfigurationSource();
        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/test");
        CorsConfiguration corsConfig = source.getCorsConfiguration(request);
        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedOriginPatterns()).contains("*");
        assertThat(corsConfig.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(corsConfig.getAllowedHeaders()).contains("*");
        assertThat(corsConfig.getAllowCredentials()).isTrue();
    }
}
