package com.facecook.report.dto;

import com.facecook.report.repository.ReportChatMessageProjection;

import java.time.LocalDateTime;

public record ReportChatMessageResponse(
        Long messageId,
        Long matchId,
        Long senderId,
        String content,
        LocalDateTime sentAt
) {
    public static ReportChatMessageResponse from(ReportChatMessageProjection message) {
        return new ReportChatMessageResponse(
                message.getMessageId(),
                message.getMatchId(),
                message.getSenderId(),
                message.getContent(),
                message.getSentAt()
        );
    }
}
