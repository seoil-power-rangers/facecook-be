package com.facecook.chat.repository;

/** {@link MessageRepository#findUnreadCountsByMatchIds} 결과 한 행(매칭 ID, 안읽음 개수). */
public interface UnreadCountProjection {
    Long getMatchId();

    Long getUnreadCount();
}
