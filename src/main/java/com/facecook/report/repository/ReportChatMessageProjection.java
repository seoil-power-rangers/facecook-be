package com.facecook.report.repository;

import java.time.LocalDateTime;

public interface ReportChatMessageProjection {
    Long getMessageId();

    Long getMatchId();

    Long getSenderId();

    String getContent();

    LocalDateTime getSentAt();
}
