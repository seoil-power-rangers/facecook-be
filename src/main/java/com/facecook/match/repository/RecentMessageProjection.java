package com.facecook.match.repository;

import java.time.LocalDateTime;

/**
 * 네이티브 쿼리 결과 한 행을 받는 틀. Spring Data가 SQL의 별칭({@code as matchId} 등)과 getter 이름을 맞춰
 * 값을 채운 구현을 만들어 준다. 엔티티로 받으면 필요 없는 컬럼까지 읽어야 해서 필요한 것만 받는다.
 */
public interface RecentMessageProjection {
    Long getMatchId();

    Long getSenderId();

    String getContent();

    LocalDateTime getSentAt();
}
