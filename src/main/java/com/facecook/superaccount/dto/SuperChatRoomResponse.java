package com.facecook.superaccount.dto;

import java.time.LocalDateTime;

public record SuperChatRoomResponse(
        Long matchId,
        LocalDateTime matchedAt,
        SuperChatMemberResponse userA,
        SuperChatMemberResponse userB,
        String lastMessage,
        LocalDateTime lastMessageAt
) {
}
