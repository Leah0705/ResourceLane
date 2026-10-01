package io.resourcelane.trade_service.Security;

import io.resourcelane.trade_service.dto.AuthCredentials;
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
 * Filter to authenticate requests using JWT stored in cookies.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Logger for tracking authentication activities. */
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Secret key used to sign and verify JWT tokens. */
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip JWT auth for internal endpoints
        String path = request.getServletPath();
        boolean shouldSkip = path.startsWith("/ws") || "/actuator/health".equals(path)
                || "/actuator/prometheus".equals(path);
        if (shouldSkip) {
            logger.debug("Skipping JWT filter for path: {}", path);
        }
        return shouldSkip;
    }

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
                        userId, new AuthCredentials(jwtToken, deviceFingerprint), null // You can set roles/authorities
                                                                                       // if needed
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
