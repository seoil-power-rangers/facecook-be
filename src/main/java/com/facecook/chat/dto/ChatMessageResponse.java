package com.facecook.chat.dto;

import com.facecook.chat.entity.Message;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 채팅 메시지 하나. REST 이력, 실시간 전달({@code /topic/chat/{matchId}}), 보낸 사람의 ACK
 * ({@code /user/queue/chat-acks}), Redis 서버 간 중계가 모두 이 모양을 쓴다.
 *
 * <p>{@code clientMessageId}는 FE가 보낼 때 만든 UUID다. FE는 이 값으로 "내가 방금 보낸 전송 중 메시지"와
 * 서버에서 돌아온 메시지를 짝지어 하나로 합친다.</p>
 */
public record ChatMessageResponse(
        Long messageId,
        Long matchId,
        Long senderId,
        String content,
        UUID clientMessageId,
        LocalDateTime sentAt
) {

    public static ChatMessageResponse from(Message message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getMatchId(),
                message.getSenderId(),
                message.getContent(),
                message.getClientMessageId(),
                message.getSentAt()
        );
    }
}
