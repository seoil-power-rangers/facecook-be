package com.facecook.chat.websocket;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.chat.service.ChatAuthorizationService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.session.SessionToken;
import com.facecook.common.session.SessionTokenSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatInboundChannelInterceptorTest {

    @Mock
    private SessionTokenSigner signer;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatAuthorizationService authorizationService;

    private ChatInboundChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ChatInboundChannelInterceptor(signer, userRepository, authorizationService);
    }

    @Test
    void verifiesParticipantWhenSubscribingToChatTopic() {
        authenticateActiveUser();
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE, "/topic/chat/20");

        interceptor.preSend(message(accessor), mock(MessageChannel.class));

        verify(authorizationService).requireParticipant(20L, 1L);
        assertThat(accessor.getUser()).isInstanceOf(ChatPrincipal.class);
    }

    @Test
    void rechecksTokenAndSuspensionOnEverySend() {
        User user = user(1L);
        user.suspend();
        when(signer.verify("signed-token")).thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        StompHeaderAccessor accessor = accessor(StompCommand.SEND, "/app/chat/20/send");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUSPENDED));

        verify(signer).verify("signed-token");
        verify(userRepository).findById(1L);
    }

    @Test
    void rejectsExpiredTokenOnLaterSend() {
        when(signer.verify("signed-token")).thenReturn(Optional.empty());
        StompHeaderAccessor accessor = accessor(StompCommand.SEND, "/app/chat/20/send");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void validatesParticipantOnAllowedSendDestination() {
        authenticateActiveUser();
        StompHeaderAccessor accessor = accessor(StompCommand.SEND, "/app/chat/20/send");

        interceptor.preSend(message(accessor), mock(MessageChannel.class));

        verify(authorizationService).requireParticipant(20L, 1L);
    }

    @Test
    void blocksDirectSendToBrokerTopic() {
        authenticateActiveUser();
        StompHeaderAccessor accessor = accessor(StompCommand.SEND, "/topic/chat/20");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void rejectsMalformedChatSubscriptionDestination() {
        authenticateActiveUser();
        StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE, "/topic/chat/not-a-number");

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
    }

    private void authenticateActiveUser() {
        when(signer.verify("signed-token")).thenReturn(Optional.of(new SessionToken(1L, 9999999999L)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
    }

    private StompHeaderAccessor accessor(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ChatSessionAttributes.SESSION_TOKEN, "signed-token");
        accessor.setSessionAttributes(attributes);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private User user(Long id) {
        User user = User.createParticipant("chat@example.com", LocalDateTime.of(2026, 9, 30, 12, 0));
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
            return user;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
