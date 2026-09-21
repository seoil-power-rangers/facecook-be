package com.facecook.cook.repository;

/**
 * 콕의 보낸 사람·받은 사람 ID만 담은 조회 결과. 두 사용자 행을 잠근 뒤에야 콕 엔티티를 읽기 위해,
 * 엔티티를 영속성 컨텍스트에 올리지 않고 쌍만 먼저 얻는 데 쓴다.
 */
public interface CookParticipants {

    Long getSenderId();

    Long getReceiverId();
}
