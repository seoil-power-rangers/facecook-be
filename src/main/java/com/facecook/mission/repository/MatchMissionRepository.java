package com.facecook.mission.repository;

import com.facecook.mission.entity.MatchMission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * {@code match_info}를 미션 관점({@link MatchMission})으로 조회. {@code findByIdForUpdate}는 그 행을
 * {@code FOR UPDATE}로 잠근다 — 두 관리자가 같은 매칭을 동시에 완료 처리해도 한 명만 성공하게 한다.
 * {@code countByCurrentStepGreaterThanEqual}은 관리자 통계(미션 완료 매칭 수)용이다.
 */
public interface MatchMissionRepository extends JpaRepository<MatchMission, Long> {
    long countByCurrentStepGreaterThanEqual(int completedStep);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mission from MatchMission mission where mission.matchId = :matchId")
    Optional<MatchMission> findByIdForUpdate(@Param("matchId") Long matchId);
}
