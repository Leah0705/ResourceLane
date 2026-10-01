package io.resourcelane.wallet_service.security;

import io.jsonwebtoken.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filter to authenticate incoming HTTP requests using JSON Web Tokens (JWT).
 * This filter intercepts every request once, extracts the JWT from cookies, verifies it against the secret key,
 * and sets the SecurityContext to establish the user's authenticated state. It also checks for device 
 * fingerprinting to prevent token theft.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * The secret key used to digitally sign the JWTs. Sourced from application configuration properties.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Determines whether this filter should be skipped for a given request.
     * Internal endpoints (e.g., used by other microservices) bypass JWT validation 
     * since they rely on internal secret headers instead.
     *
     * @param request The incoming HTTP request.
     * @return true if the request path starts with "/api/wallet/internal", false otherwise.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip JWT auth for internal endpoints
        String path = request.getServletPath();
        boolean shouldSkip = path.startsWith("/api/wallet/internal") || "/actuator/health".equals(path)
                || "/actuator/prometheus".equals(path);
        if (shouldSkip) {
            logger.debug("Skipping JWT filter for internal path: {}", path);
        }
        return shouldSkip;
    }

    /**
     * Core logic of the filter. It extracts the JWT from the "jwt" cookie, validates its signature,
     * checks for expiration, and validates the device fingerprint to prevent session hijacking.
     * If valid, an Authentication token is created and placed in the SecurityContext.
     *
     * @param request     The incoming HTTP request.
     * @param response    The outgoing HTTP response.
     * @param filterChain The chain of filters to pass the request along to.
     * @throws ServletException If an error occurs during filtering.
     * @throws IOException      If an input or output error occurs.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

    
        
        String jwtToken = null;

        // Read JWT from cookies
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    jwtToken = cookie.getValue();
                    break;
                }
            }
        }

        // Proceed if token exists
        if (jwtToken != null) {
            try {
                Claims claims = Jwts.parser()
                        .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                        .parseClaimsJws(jwtToken)
                        .getBody();

                Long userId = claims.get("userId", Long.class);
                String deviceFingerprint = claims.get("deviceFingerprint", String.class);

                String requestFingerprint = request.getHeader("X-Device-Fingerprint");
                if (requestFingerprint == null || !requestFingerprint.equals(deviceFingerprint)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Unauthorized: Device fingerprint mismatch");
                    return;
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userId, null, null // You can set roles/authorities if needed
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException e) {
                // Token invalid or expired
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Unauthorized: Invalid or expired token");
                return;
            }
        } else {
            // No token found
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Token not found");
            return;
        }

        filterChain.doFilter(request, response);
    }

    // @Override
    // protected boolean shouldNotFilter(HttpServletRequest request) {
    //     return request.getRequestURI().contains("/internal/updateWallet");
    // }

}
