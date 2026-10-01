package io.resourcelane.trade_service.webSocket;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import io.jsonwebtoken.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interceptor for WebSocket handshakes to validate JWT and device fingerprints.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    /** Logger for tracking handshake and authentication processes. */
    private static final Logger logger = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    /** Secret key used to verify the incoming JWT token signature. */
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {
        try {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                HttpServletRequest req = servletRequest.getServletRequest();
                Cookie[] cookies = req.getCookies();

                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if ("jwt".equals(cookie.getName())) {
                            String jwtToken = cookie.getValue();

                            Claims claims = Jwts.parser()
                                    .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                                    .parseClaimsJws(jwtToken)
                                    .getBody();

                            Long userId = claims.get("userId", Long.class);
                            String deviceFingerprint = claims.get("deviceFingerprint", String.class);
                            String requestFingerprint = req.getParameter("fingerprint");

                            if (requestFingerprint == null || !requestFingerprint.equals(deviceFingerprint)) {
                                logger.warn("Fingerprint mismatch for user {}. JWT: {}, Request: {}", userId, deviceFingerprint, requestFingerprint);
                                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                                return false;
                            }

                            attributes.put("userId", userId);
                            logger.info("WebSocket handshake allowed for user {}", userId);
                            return true;
                        }
                    }
                }
            }

            logger.warn("JWT cookie not found during WebSocket handshake");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;

        } catch (Exception e) {
            logger.error("Exception in WebSocket handshake: {}", e.getMessage(), e);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        if (exception != null) {
            logger.error("Handshake exception", exception);
        }
    }
}
