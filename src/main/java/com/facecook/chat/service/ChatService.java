package com.facecook.chat.service;

import com.facecook.chat.config.ChatOperatingHoursProperties;
import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.dto.SendChatMessageRequest;
import com.facecook.chat.entity.Message;
import com.facecook.chat.repository.MessageRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.match.entity.MatchInfo;
import com.facecook.push.service.ParticipantPushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 매칭방 채팅 메시지의 조회·전송.
 *
 * <p>전송은 클라이언트가 보낸 {@code clientMessageId}(UUID)로 멱등하게
 * 처리한다 — 네트워크 재시도로 같은 요청이 두 번 와도 메시지가 두 번
 * 저장되지 않는다({@link #send} 참고).</p>
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    private final MessageRepository messageRepository;
    private final ChatMessagePageReader pageReader;
    private final ChatAuthorizationService authorizationService;
    private final ParticipantPushNotificationService pushNotificationService;
    private final ChatOperatingHoursProperties operatingHoursProperties;
    private final Clock clock;

    /**
     * matchId 채팅방의 메시지를 id 역순(최신 먼저)으로 최대 limit개
     * 조회한다. before가 있으면 그 메시지 id보다 오래된 것만(과거 페이지
     * 넘기기용).
     *
     * <p>전제조건: matchId 존재, userId가 그 매칭의 당사자.</p>
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음), {@code FORBIDDEN}(당사자
     * 아님).</p>
     *
     * @see #send(Long, Long, SendChatMessageRequest)
     */
    public List<ChatMessageResponse> getHistory(Long userId, Long matchId, Long before, int limit) {
        authorizationService.requireParticipant(matchId, userId);
        return pageReader.read(matchId, before, limit);
    }

    /**
     * senderId가 matchId 채팅방에 메시지를 보낸다.
     * {@code request.clientMessageId()}가 이미 저장된 것과 같으면 새로
     * 저장하지 않고 기존 메시지를 그대로 돌려준다(같은 요청이 재전송돼도
     * 중복 저장 안 됨).
     *
     * <p>전제조건: 현재 시각이 채팅 운영시간 안(
     * {@link ChatOperatingHoursProperties}), matchId 존재, senderId가
     * 당사자.</p>
     *
     * <p>부작용: {@code result.created()}가 true일 때만 {@code Message}를
     * 새로 저장하고 상대에게 chatMessageReceived 푸시를 보낸다. 동시에
     * 같은 clientMessageId로 두 요청이 들어와도 DB UNIQUE 제약이 하나만
     * 통과시키고, 진 쪽은 방금 저장된 걸 재조회해서 같은 결과로 수렴한다
     * (메시지 유실·중복 없음).</p>
     *
     * <p>예외: {@code CLOSED}(운영시간 아님), {@code NOT_FOUND},
     * {@code FORBIDDEN}, {@code VALIDATION}(clientMessageId가 다른
     * 사람·다른 매칭 것과 충돌 — UUID 재사용 시도로 간주).</p>
     *
     * @see #getHistory(Long, Long, Long, int)
     */
    public ChatSendResult send(Long senderId, Long matchId, SendChatMessageRequest request) {
        LocalDateTime now = now();
        ensureOperatingHours(now.toLocalTime());
        MatchInfo matchInfo = authorizationService.requireParticipant(matchId, senderId);

        ChatSendResult result = messageRepository.findByClientMessageId(request.clientMessageId())
                .map(message -> existingResult(message, senderId, matchId))
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
                    .map(message -> existingResult(message, senderId, matchId))
                    .orElseThrow(() -> exception);
        }
    }

    /** 이미 저장된 메시지를 재전송 결과({@code created=false})로 돌려준다. 첫 조회와 저장 충돌 뒤 재조회가 같이 쓴다. */
    private ChatSendResult existingResult(Message message, Long senderId, Long matchId) {
        return new ChatSendResult(verifySenderAndRoomThenConvert(message, senderId, matchId), false);
    }

    /**
     * 기존 메시지가 같은 발신자·같은 채팅방의 것인지 검사하고 응답으로 바꾼다. 발신자만
     * 보면 안 된다 — 같은 사람이 다른 방에서 UUID를 재사용해도 막아야 한다.
     */
    private ChatMessageResponse verifySenderAndRoomThenConvert(Message message, Long senderId, Long matchId) {
        if (!message.getSenderId().equals(senderId) || !message.getMatchId().equals(matchId)) {
            // 전역 UNIQUE UUID를 다른 사용자나 채팅방에서 재사용해 기존 메시지를
            // 열람하는 경로가 되지 않도록 충돌 정보는 노출하지 않는다.
            throw new ApiException(ErrorCode.VALIDATION, "clientMessageId가 올바르지 않습니다.");
        }
        return ChatMessageResponse.from(message);
    }

    private void ensureOperatingHours(LocalTime time) {
        if (!operatingHoursProperties.contains(time)) {
            throw new ApiException(ErrorCode.CLOSED);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), EVENT_ZONE);
    }
}
