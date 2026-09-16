package com.facecook.mission.websocket;

import com.facecook.auth.entity.UserRole;
import com.facecook.chat.websocket.ChatPrincipal;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.mission.service.MissionAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MissionInboundChannelInterceptorTest {

    private MissionAuthorizationService authorizationService;
    private MissionInboundChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        authorizationService = mock(MissionAuthorizationService.class);
        interceptor = new MissionInboundChannelInterceptor(authorizationService);
    }

    @Test
    void onlyMatchParticipantCanSubscribe() {
        StompHeaderAccessor accessor = accessor("/topic/mission/20");
        accessor.setUser(new ChatPrincipal(
                new AuthenticatedUser(1L, "participant@example.com", UserRole.PARTICIPANT)
        ));

        interceptor.preSend(message(accessor), mock(MessageChannel.class));

        verify(authorizationService).requireParticipant(20L, 1L);
    }

    @Test
    void rejectsMissionSubscriptionWithoutAuthenticatedPrincipal() {
        StompHeaderAccessor accessor = accessor("/topic/mission/20");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void rejectsMalformedMissionTopic() {
        StompHeaderAccessor accessor = accessor("/topic/mission/not-a-number");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
    }

    private static StompHeaderAccessor accessor(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
