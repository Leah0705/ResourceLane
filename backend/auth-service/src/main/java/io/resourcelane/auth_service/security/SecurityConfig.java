package io.resourcelane.auth_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Security configuration for the Auth Service.
 * Configures JWT authentication, CORS, and request authorization rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Filter that processes JWT tokens for authenticating requests.
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Custom authentication provider that handles OTP-based authentication.
     */
    private final OtpAuthenticationProvider otpAuthenticationProvider;

    /**
     * Constructs the SecurityConfig with necessary authentication components.
     * 
     * @param jwtAuthenticationFilter   Filter for JWT validation.
     * @param otpAuthenticationProvider Provider for OTP validation.
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, OtpAuthenticationProvider otpAuthenticationProvider) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.otpAuthenticationProvider = otpAuthenticationProvider;
    }

    /**
     * Configures the AuthenticationManager with the custom OTP authentication provider.
     *
     * @param http The HttpSecurity object.
     * @return The configured AuthenticationManager.
     * @throws Exception If configuration fails.
     */
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.authenticationProvider(otpAuthenticationProvider);
        return builder.build();
    }

    /**
     * Configures the SecurityFilterChain for HTTP requests.
     *
     * @param http The HttpSecurity object.
     * @return The configured SecurityFilterChain.
     * @throws Exception If configuration fails.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/auth/getUser", "/api/auth/logout","/api/auth/getUserId").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Configures CORS settings.
     *
     * @return The CorsConfigurationSource.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
