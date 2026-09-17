package com.facecook.chat.repository;

public interface UnreadCountProjection {
    Long getMatchId();

    Long getUnreadCount();
}
