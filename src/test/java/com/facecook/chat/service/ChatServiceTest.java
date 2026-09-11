package com.facecook.chat.service;

import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.dto.SendChatMessageRequest;
import com.facecook.chat.entity.Message;
import com.facecook.chat.repository.MessageRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.entity.MatchInfo;
import com.facecook.push.service.ParticipantPushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MATCH_ID = 20L;
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString("32fa481f-e623-49c3-9fa4-b47bf3dc84cc");

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatAuthorizationService authorizationService;

    @Mock
    private ParticipantPushNotificationService pushNotificationService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = serviceAt("2026-09-30T03:00:00Z"); // Asia/Seoul 12:00
    }

    @Test
    void returnsNewestHistoryBeforeCursor() {
        Message message = message(99L, MATCH_ID, USER_ID, CLIENT_MESSAGE_ID);
        PageRequest page = PageRequest.of(0, 25);
        when(messageRepository.findByMatchIdAndIdLessThanOrderByIdDesc(MATCH_ID, 100L, page))
                .thenReturn(List.of(message));

        List<ChatMessageResponse> result = chatService.getHistory(USER_ID, MATCH_ID, 100L, 25);

        verify(authorizationService).requireParticipant(MATCH_ID, USER_ID);
        assertThat(result).extracting(ChatMessageResponse::messageId).containsExactly(99L);
    }

    @Test
    void savesMessageDuringOperatingHours() {
        SendChatMessageRequest request = request();
        givenMatch();
        when(messageRepository.findByClientMessageId(CLIENT_MESSAGE_ID)).thenReturn(Optional.empty());
        when(messageRepository.saveAndFlush(any(Message.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 101L));

        ChatSendResult result = chatService.send(USER_ID, MATCH_ID, request);

        verify(authorizationService).requireParticipant(MATCH_ID, USER_ID);
        assertThat(result.created()).isTrue();
        assertThat(result.message().messageId()).isEqualTo(101L);
        assertThat(result.message().clientMessageId()).isEqualTo(CLIENT_MESSAGE_ID);
        assertThat(result.message().sentAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 12, 0));
        verify(pushNotificationService).chatMessageReceived(2L, MATCH_ID);
    }

    @Test
    void returnsExistingMessageForRepeatedClientMessageId() {
        Message existing = message(101L, MATCH_ID, USER_ID, CLIENT_MESSAGE_ID);
        when(messageRepository.findByClientMessageId(CLIENT_MESSAGE_ID)).thenReturn(Optional.of(existing));

        ChatSendResult result = chatService.send(USER_ID, MATCH_ID, request());

        assertThat(result).isEqualTo(new ChatSendResult(ChatMessageResponse.from(existing), false));
        verify(messageRepository, never()).saveAndFlush(any());
        verify(pushNotificationService, never()).chatMessageReceived(any(), any());
    }

    @Test
    void convergesOnExistingMessageWhenConcurrentInsertWins() {
        Message existing = message(101L, MATCH_ID, USER_ID, CLIENT_MESSAGE_ID);
        when(messageRepository.findByClientMessageId(CLIENT_MESSAGE_ID))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(messageRepository.saveAndFlush(any(Message.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        ChatSendResult result = chatService.send(USER_ID, MATCH_ID, request());

        assertThat(result).isEqualTo(new ChatSendResult(ChatMessageResponse.from(existing), false));
        verify(pushNotificationService, never()).chatMessageReceived(any(), any());
    }

    @Test
    void doesNotLeakMessageWhenUuidIsReusedByAnotherChat() {
        Message anotherMessage = message(101L, 999L, 888L, CLIENT_MESSAGE_ID);
        when(messageRepository.findByClientMessageId(CLIENT_MESSAGE_ID)).thenReturn(Optional.of(anotherMessage));

        assertThatThrownBy(() -> chatService.send(USER_ID, MATCH_ID, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-29T23:59:59Z", "2026-09-30T09:00:00Z"})
    void rejectsSendOutsideNineToEighteenSeoulTime(String instant) {
        chatService = serviceAt(instant);

        assertThatThrownBy(() -> chatService.send(USER_ID, MATCH_ID, request()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CLOSED));
        verify(authorizationService, never()).requireParticipant(any(), any());
    }

    @Test
    void acceptsExactlyAtNineSeoulTime() {
        chatService = serviceAt("2026-09-30T00:00:00Z");
        givenMatch();
        when(messageRepository.findByClientMessageId(CLIENT_MESSAGE_ID)).thenReturn(Optional.empty());
        when(messageRepository.saveAndFlush(any(Message.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 101L));

        ChatSendResult result = chatService.send(USER_ID, MATCH_ID, request());

        assertThat(result.message().sentAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 9, 0));
    }

    private ChatService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new ChatService(messageRepository, authorizationService, pushNotificationService, clock);
    }

    private void givenMatch() {
        when(authorizationService.requireParticipant(MATCH_ID, USER_ID))
                .thenReturn(MatchInfo.create(USER_ID, 2L, LocalDateTime.of(2026, 9, 30, 11, 0)));
    }

    private SendChatMessageRequest request() {
        return new SendChatMessageRequest("안녕하세요", CLIENT_MESSAGE_ID);
    }

    private Message message(Long id, Long matchId, Long senderId, UUID clientMessageId) {
        return withId(Message.create(
                matchId,
                senderId,
                "안녕하세요",
                clientMessageId,
                LocalDateTime.of(2026, 9, 30, 12, 0)
        ), id);
    }

    private static Message withId(Message message, Long id) {
        try {
            Field field = Message.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(message, id);
            return message;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
