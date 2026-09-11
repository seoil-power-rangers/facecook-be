package com.facecook.chat.dto;

import com.facecook.chat.entity.Message;

import java.time.LocalDateTime;
import java.util.UUID;

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
