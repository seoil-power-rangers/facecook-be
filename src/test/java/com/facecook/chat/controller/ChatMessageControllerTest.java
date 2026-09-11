package com.facecook.chat.controller;

import com.facecook.auth.entity.UserRole;
import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.dto.SendChatMessageRequest;
import com.facecook.chat.redis.ChatMessagePublisher;
import com.facecook.chat.service.ChatSendResult;
import com.facecook.chat.service.ChatService;
import com.facecook.chat.websocket.ChatPrincipal;
import com.facecook.common.session.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageControllerTest {

    private final ChatService chatService = mock(ChatService.class);
    private final ChatMessagePublisher publisher = mock(ChatMessagePublisher.class);
    private final ChatMessageController controller = new ChatMessageController(chatService, publisher);

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
}
