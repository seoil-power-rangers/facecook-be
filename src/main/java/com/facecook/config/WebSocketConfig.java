package com.facecook.config;

import com.facecook.chat.websocket.ChatHandshakeHandler;
import com.facecook.chat.websocket.ChatHandshakeInterceptor;
import com.facecook.chat.websocket.ChatInboundChannelInterceptor;
import com.facecook.chat.websocket.ChatStompErrorHandler;
import com.facecook.mission.websocket.MissionInboundChannelInterceptor;
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
    private final MissionInboundChannelInterceptor missionInboundChannelInterceptor;
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
        // 채팅 인터셉터가 모든 CONNECT/SUBSCRIBE에서 세션을 재검증하고
        // Principal을 설정한 뒤, 미션 인터셉터가 매칭 참가자 권한을 검사한다.
        registration.interceptors(inboundChannelInterceptor, missionInboundChannelInterceptor);
    }
}
