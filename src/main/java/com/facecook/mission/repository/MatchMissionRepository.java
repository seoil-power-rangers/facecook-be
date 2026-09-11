package com.facecook.mission.repository;

import com.facecook.mission.entity.MatchMission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MatchMissionRepository extends JpaRepository<MatchMission, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mission from MatchMission mission where mission.matchId = :matchId")
    Optional<MatchMission> findByIdForUpdate(@Param("matchId") Long matchId);
}
