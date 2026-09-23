package com.facecook.chat.controller;

import com.facecook.auth.entity.UserRole;
import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.dto.SendChatMessageRequest;
import com.facecook.chat.redis.ChatMessagePublisher;
import com.facecook.chat.service.ChatSendResult;
import com.facecook.chat.service.ChatService;
import com.facecook.chat.websocket.ChatPrincipal;
import com.facecook.chat.websocket.ChatStompErrorHandler;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.session.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageControllerTest {

    private final ChatService chatService = mock(ChatService.class);
    private final ChatMessagePublisher publisher = mock(ChatMessagePublisher.class);
    private final ChatStompErrorHandler errorHandler = new ChatStompErrorHandler(new ObjectMapper());
    private final MessageChannel clientOutboundChannel = mock(MessageChannel.class);
    private final ChatMessageController controller =
            new ChatMessageController(chatService, publisher, errorHandler, clientOutboundChannel);

    @Test
    void returnsPersistedMessageAsAckAndPublishesItToRedis() {
        UUID clientMessageId = UUID.fromString("32fa481f-e623-49c3-9fa4-b47bf3dc84cc");
        SendChatMessageRequest request = new SendChatMessageRequest("안녕하세요", clientMessageId);
        ChatMessageResponse response = new ChatMessageResponse(
                101L,
                20L,
                1L,
                "안녕하세요",
                clientMessageId,
                LocalDateTime.of(2026, 9, 30, 12, 0)
        );
        ChatPrincipal principal = new ChatPrincipal(
                new AuthenticatedUser(1L, "chat@example.com", UserRole.PARTICIPANT)
        );
        when(chatService.send(1L, 20L, request)).thenReturn(new ChatSendResult(response, true));

        ChatMessageResponse result = controller.send(20L, request, principal);

        assertThat(result).isEqualTo(response);
        verify(publisher).publish(response);
    }

    @Test
    void acknowledgesSavedMessageEvenWhenRedisPublishFails() {
        UUID clientMessageId = UUID.fromString("32fa481f-e623-49c3-9fa4-b47bf3dc84cc");
        SendChatMessageRequest request = new SendChatMessageRequest("안녕하세요", clientMessageId);
        ChatMessageResponse response = new ChatMessageResponse(
                101L,
                20L,
                1L,
                "안녕하세요",
                clientMessageId,
                LocalDateTime.of(2026, 9, 30, 12, 0)
        );
        ChatPrincipal principal = new ChatPrincipal(
                new AuthenticatedUser(1L, "chat@example.com", UserRole.PARTICIPANT)
        );
        when(chatService.send(1L, 20L, request)).thenReturn(new ChatSendResult(response, true));
        org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"))
                .when(publisher).publish(response);

        ChatMessageResponse result = controller.send(20L, request, principal);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void acknowledgesExistingMessageWithoutPublishingDuplicate() {
        UUID clientMessageId = UUID.fromString("32fa481f-e623-49c3-9fa4-b47bf3dc84cc");
        SendChatMessageRequest request = new SendChatMessageRequest("재전송", clientMessageId);
        ChatMessageResponse existing = new ChatMessageResponse(
                101L,
                20L,
                1L,
                "최초 내용",
                clientMessageId,
                LocalDateTime.of(2026, 9, 30, 12, 0)
        );
        ChatPrincipal principal = new ChatPrincipal(
                new AuthenticatedUser(1L, "chat@example.com", UserRole.PARTICIPANT)
        );
        when(chatService.send(1L, 20L, request)).thenReturn(new ChatSendResult(existing, false));

        ChatMessageResponse result = controller.send(20L, request, principal);

        assertThat(result).isEqualTo(existing);
        org.mockito.Mockito.verifyNoInteractions(publisher);
    }

    @Test
    void handleExceptionSendsErrorFrameToOriginatingSessionOnClientOutboundChannel() {
        // @MessageMapping 핸들러 안에서 던진 예외는 StompSubProtocolErrorHandler를
        // 거치지 않고 별도 스레드에서 조용히 사라지던 버그 재현·수정 확인용.
        // (facecook-be#XX)
        ApiException exception = new ApiException(ErrorCode.CLOSED);

        controller.handleException(exception, "session-42");

        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(clientOutboundChannel).send(captor.capture());

        @SuppressWarnings("unchecked")
        Message<byte[]> sent = (Message<byte[]>) captor.getValue();
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(sent);
        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getSessionId()).isEqualTo("session-42");
        assertThat(new String(sent.getPayload())).contains("\"code\":\"CLOSED\"");
    }

    @Test
    void handleExceptionSwallowsOutboundChannelFailureInsteadOfPropagating() {
        when(clientOutboundChannel.send(any())).thenThrow(
                new org.springframework.messaging.MessageDeliveryException("broker down")
        );

        controller.handleException(new ApiException(ErrorCode.CLOSED), "session-1");

        // 예외 없이 끝나면 성공 — 알림 전송 실패가 STOMP 처리 자체를 깨뜨리지 않는다.
    }
}
