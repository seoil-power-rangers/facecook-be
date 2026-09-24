package com.facecook.report.repository;

import java.time.LocalDateTime;

/**
 * {@link ReportRepository#findChatMessages} 네이티브 쿼리의 한 행. Spring Data가 쿼리의 열 별칭
 * ({@code as messageId} 등)을 같은 이름의 getter({@code getMessageId()})에 연결해 구현체를 만들어 준다.
 * 엔티티가 아니라서 조회한 값을 바꿔도 DB에 저장되지 않는다.
 */
public interface ReportChatMessageProjection {
    Long getMessageId();

    Long getMatchId();

    Long getSenderId();

    String getContent();

    LocalDateTime getSentAt();
}
