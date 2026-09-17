package com.facecook.cook.repository;

import java.time.LocalDateTime;

public interface RecentMessageProjection {
    Long getMatchId();

    Long getSenderId();

    String getContent();

    LocalDateTime getSentAt();
}
