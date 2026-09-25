package com.facecook.superaccount.dto;

import java.time.LocalDateTime;

/** 슈퍼 채팅방 목록의 한 줄. 메시지가 없는 방은 {@code lastMessage}·{@code lastMessageAt}이 null이다. */
public record SuperChatRoomResponse(
        Long matchId,
        LocalDateTime matchedAt,
        SuperChatMemberResponse userA,
        SuperChatMemberResponse userB,
        String lastMessage,
        LocalDateTime lastMessageAt
) {
}
