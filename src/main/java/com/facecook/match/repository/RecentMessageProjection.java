package com.facecook.match.repository;

import java.time.LocalDateTime;

public interface RecentMessageProjection {
    Long getMatchId();

    Long getSenderId();

    String getContent();

    LocalDateTime getSentAt();
}
