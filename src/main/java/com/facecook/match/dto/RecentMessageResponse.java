package com.facecook.match.dto;

import com.facecook.match.repository.RecentMessageProjection;

import java.time.LocalDateTime;

/** 매칭 목록에 미리 보이는 마지막 메시지. {@code MatchInfoRepository#findRecentMessagesByMatchIds} 결과에서 만든다. */
public record RecentMessageResponse(
        Long senderId,
        String content,
        LocalDateTime sentAt
) {
    public static RecentMessageResponse from(RecentMessageProjection message) {
        return new RecentMessageResponse(message.getSenderId(), message.getContent(), message.getSentAt());
    }
}
