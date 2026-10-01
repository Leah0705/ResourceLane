package io.resourcelane.auth_service.security;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Filter to authenticate requests using JWT stored in cookies.
 * Extracts JWT, validates it, and checks device fingerprint.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * The secret key used to parse and validate the signature of incoming JWT tokens.
     * Injected from the application properties.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Determines whether the current request should bypass this filter.
     * We skip filtering for specific endpoints like getting user info or logging out,
     * as these either require different handling or are already authenticated in other ways.
     * 
     * @param request The incoming HTTP request.
     * @return true if the filter should not be applied to this request; false otherwise.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only filter specific protected endpoints
        String path = request.getServletPath();
        return "/actuator/health".equals(path)
                || !("/api/auth/getUser".equals(path) || "/api/auth/logout".equals(path)|| "/api/auth/getUserId".equals(path));
    }

    /**
     * Core logic of the filter: extracts the JWT from cookies, validates its signature
     * and claims (including device fingerprint), and sets the authentication context if valid.
     * This ensures that only authenticated requests with matching device fingerprints can proceed
     * to access secured endpoints.
     * 
     * @param request     The incoming HTTP request containing the JWT cookie and fingerprint header.
     * @param response    The HTTP response to write error messages to if authentication fails.
     * @param filterChain The filter chain to continue request processing if authenticated.
     * @throws ServletException If a servlet-specific error occurs during filtering.
     * @throws IOException      If an I/O error occurs during filtering.
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

}
