package com.facecook.mission.websocket;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.chat.service.ChatAuthorizationService;
import com.facecook.chat.websocket.ChatHandshakeHandler;
import com.facecook.chat.websocket.ChatHandshakeInterceptor;
import com.facecook.chat.websocket.ChatInboundChannelInterceptor;
import com.facecook.chat.websocket.ChatPrincipal;
import com.facecook.chat.websocket.ChatSessionAttributes;
import com.facecook.chat.websocket.ChatStompErrorHandler;
import com.facecook.common.session.SessionToken;
import com.facecook.common.session.SessionTokenSigner;
import com.facecook.config.CorsProperties;
import com.facecook.config.WebSocketConfig;
import com.facecook.mission.service.MissionAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionWebSocketInterceptorChainTest {

    @Test
    void authenticatesThenAuthorizesMissionSubscriptionInConfiguredOrder() {
        SessionTokenSigner signer = mock(SessionTokenSigner.class);
        UserRepository userRepository = mock(UserRepository.class);
        ChatAuthorizationService chatAuthorizationService = mock(ChatAuthorizationService.class);
        MissionAuthorizationService missionAuthorizationService = mock(MissionAuthorizationService.class);
        ChatInboundChannelInterceptor chatInterceptor = new ChatInboundChannelInterceptor(
                signer,
                userRepository,
                chatAuthorizationService
        );
        MissionInboundChannelInterceptor missionInterceptor =
                new MissionInboundChannelInterceptor(missionAuthorizationService);
        WebSocketConfig config = new WebSocketConfig(
                mock(CorsProperties.class),
                mock(ChatHandshakeInterceptor.class),
                mock(ChatHandshakeHandler.class),
                chatInterceptor,
                missionInterceptor,
                mock(ChatStompErrorHandler.class)
        );
        TestChannelRegistration registration = new TestChannelRegistration();
        config.configureClientInboundChannel(registration);

        User user = user(1L);
        when(signer.verify("signed-token"))
                .thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/mission/20");
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(ChatSessionAttributes.SESSION_TOKEN, "signed-token");
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        MessageChannel channel = mock(MessageChannel.class);

        for (ChannelInterceptor interceptor : registration.registeredInterceptors()) {
            message = interceptor.preSend(message, channel);
        }

        assertThat(accessor.getUser()).isInstanceOf(ChatPrincipal.class);
        verify(missionAuthorizationService).requireParticipant(20L, 1L);
    }

    private static User user(Long id) {
        User user = User.createParticipant(
                "mission@example.com",
                LocalDateTime.of(2026, 9, 30, 12, 0)
        );
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
            return user;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class TestChannelRegistration extends ChannelRegistration {
        List<ChannelInterceptor> registeredInterceptors() {
            return getInterceptors();
        }
    }
}
