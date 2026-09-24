package com.facecook.mission.repository;

import com.facecook.mission.entity.MatchMissionAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 매칭별 미션 배정 조회·저장. 배정을 읽을 때 미션 문구를 함께 가져온다({@code @EntityGraph}). */
public interface MatchMissionAssignmentRepository extends JpaRepository<MatchMissionAssignment, Long> {

    @EntityGraph(attributePaths = "template")
    List<MatchMissionAssignment> findAllByMatchIdOrderByStep(Long matchId);
}
