package com.facecook.mission.repository;

import com.facecook.mission.entity.MatchMissionAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchMissionAssignmentRepository extends JpaRepository<MatchMissionAssignment, Long> {

    @EntityGraph(attributePaths = "template")
    List<MatchMissionAssignment> findAllByMatchIdOrderByStep(Long matchId);
}
