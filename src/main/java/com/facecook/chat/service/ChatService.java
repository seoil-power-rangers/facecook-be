package com.facecook.chat.service;

import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.dto.SendChatMessageRequest;
import com.facecook.chat.entity.Message;
import com.facecook.chat.repository.MessageRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.entity.MatchInfo;
import com.facecook.push.service.ParticipantPushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(18, 0);

    private final MessageRepository messageRepository;
    private final ChatAuthorizationService authorizationService;
    private final ParticipantPushNotificationService pushNotificationService;
    private final Clock clock;

    public List<ChatMessageResponse> getHistory(Long userId, Long matchId, Long before, int limit) {
        authorizationService.requireParticipant(matchId, userId);
        PageRequest page = PageRequest.of(0, limit);
        List<Message> messages = before == null
                ? messageRepository.findByMatchIdOrderByIdDesc(matchId, page)
                : messageRepository.findByMatchIdAndIdLessThanOrderByIdDesc(matchId, before, page);
        return messages.stream().map(ChatMessageResponse::from).toList();
    }

    public ChatSendResult send(Long senderId, Long matchId, SendChatMessageRequest request) {
        LocalDateTime now = now();
        ensureOperatingHours(now.toLocalTime());
        MatchInfo matchInfo = authorizationService.requireParticipant(matchId, senderId);

        ChatSendResult result = messageRepository.findByClientMessageId(request.clientMessageId())
                .map(message -> new ChatSendResult(existingMessage(message, senderId, matchId), false))
                .orElseGet(() -> saveOrFindExisting(senderId, matchId, request, now));
        if (result.created()) {
            pushNotificationService.chatMessageReceived(matchInfo.otherUserId(senderId), matchId);
        }
        return result;
    }

    private ChatSendResult saveOrFindExisting(
            Long senderId,
            Long matchId,
            SendChatMessageRequest request,
            LocalDateTime sentAt
    ) {
        try {
            Message saved = messageRepository.saveAndFlush(Message.create(
                    matchId,
                    senderId,
                    request.content(),
                    request.clientMessageId(),
                    sentAt
            ));
            return new ChatSendResult(ChatMessageResponse.from(saved), true);
        } catch (DataIntegrityViolationException exception) {
            // saveAndFlush 자체 트랜잭션이 롤백된 뒤 다시 조회하므로 동시 재전송도
            // client_message_id UNIQUE 제약을 기준으로 같은 메시지에 수렴한다.
            return messageRepository.findByClientMessageId(request.clientMessageId())
                    .map(message -> new ChatSendResult(existingMessage(message, senderId, matchId), false))
                    .orElseThrow(() -> exception);
        }
    }

    private ChatMessageResponse existingMessage(Message message, Long senderId, Long matchId) {
        if (!message.getSenderId().equals(senderId) || !message.getMatchId().equals(matchId)) {
            // 전역 UNIQUE UUID를 다른 사용자나 채팅방에서 재사용해 기존 메시지를
            // 열람하는 경로가 되지 않도록 충돌 정보는 노출하지 않는다.
            throw new ApiException(ErrorCode.VALIDATION, "clientMessageId가 올바르지 않습니다.");
        }
        return ChatMessageResponse.from(message);
    }

    private void ensureOperatingHours(LocalTime time) {
        if (time.isBefore(OPEN_TIME) || !time.isBefore(CLOSE_TIME)) {
            throw new ApiException(ErrorCode.CLOSED);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), EVENT_ZONE);
    }
}
