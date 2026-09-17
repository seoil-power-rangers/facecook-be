package com.facecook.chat.repository;

import java.time.LocalDateTime;

public interface UnreadMessageProjection {
    Long getMatchId();

    LocalDateTime getSentAt();
}
