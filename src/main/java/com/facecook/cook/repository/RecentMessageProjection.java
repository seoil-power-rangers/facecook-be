package com.facecook.cook.repository;

import java.time.LocalDateTime;

public interface RecentMessageProjection {
    Long getSenderId();

    String getContent();

    LocalDateTime getSentAt();
}
