package com.facecook.cook.dto;

import com.facecook.cook.repository.RecentMessageProjection;

import java.time.LocalDateTime;

public record RecentMessageResponse(
        Long senderId,
        String content,
        LocalDateTime sentAt
) {
    public static RecentMessageResponse from(RecentMessageProjection message) {
        return new RecentMessageResponse(message.getSenderId(), message.getContent(), message.getSentAt());
    }
}
