package io.resourcelane.trade_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import io.resourcelane.trade_service.webSocket.JwtHandshakeInterceptor;

/**
 * Configuration for WebSocket message brokering.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Interceptor to perform JWT authentication during the initial WebSocket handshake. */
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * Constructor for WebSocketConfig.
     *
     * @param jwtHandshakeInterceptor The interceptor for JWT validation during handshake.
     */
    WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    /**
     * Configures the message broker.
     *
     * @param config The MessageBrokerRegistry.
     */
    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers STOMP endpoints.
     *
     * @param registry The StompEndpointRegistry.
     */
    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor);
    }
}
