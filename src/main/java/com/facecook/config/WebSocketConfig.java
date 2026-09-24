package com.facecook.config;

import com.facecook.chat.websocket.ChatHandshakeHandler;
import com.facecook.chat.websocket.ChatHandshakeInterceptor;
import com.facecook.chat.websocket.ChatInboundChannelInterceptor;
import com.facecook.chat.websocket.ChatStompErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
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

    /**
     * 서버·클라이언트 STOMP 하트비트 간격(ms). FE({@code chatSocket.ts}, {@code missionSocket.ts})도 10초로
     * 보내고 받는다. 서버가 하트비트를 요구하지 않으면 휴대폰 네트워크 전환처럼 조용히 끊긴 연결을 서버가
     * 알아채지 못한다 — 그 연결의 접속 기록을 계속 연장해서 푸시가 막힌다(#81). 요구하면 클라이언트
     * 하트비트가 끊긴 연결을 브로커가 닫고, 연결 종료 이벤트로 접속 기록도 지워진다.
     */
    private static final long[] STOMP_HEARTBEAT_MS = {10_000, 10_000};

    private TaskScheduler messageBrokerTaskScheduler;

    @Autowired
    public void setMessageBrokerTaskScheduler(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler scheduler) {
        this.messageBrokerTaskScheduler = scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(STOMP_HEARTBEAT_MS)
                .setTaskScheduler(messageBrokerTaskScheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT/SUBSCRIBE/SEND마다 세션을 재검증하고, 구독은 허용 목록
        // (StompSubscriptionPolicy), 전송은 채팅 전송 경로만 통과시킨다.
        registration.interceptors(inboundChannelInterceptor);
    }
}
