package com.facecook.report.dto;

import com.facecook.report.repository.ReportChatMessageProjection;

import java.time.LocalDateTime;

/** 신고 화면의 채팅 이력 한 줄. {@link ReportChatMessageProjection}(네이티브 쿼리 결과)을 그대로 옮긴다. */
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
