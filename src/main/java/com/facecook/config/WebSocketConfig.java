package com.facecook.config;

import com.facecook.chat.websocket.ChatHandshakeHandler;
import com.facecook.chat.websocket.ChatHandshakeInterceptor;
import com.facecook.chat.websocket.ChatInboundChannelInterceptor;
import com.facecook.chat.websocket.ChatStompErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final ChatHandshakeInterceptor handshakeInterceptor;
    private final ChatHandshakeHandler handshakeHandler;
    private final ChatInboundChannelInterceptor inboundChannelInterceptor;
    private final ChatStompErrorHandler errorHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler);
        registry.setErrorHandler(errorHandler);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundChannelInterceptor);
    }
}
