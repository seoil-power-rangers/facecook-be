package com.facecook.mission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "match_mission_assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchMissionAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "step", nullable = false)
    private int step;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_template_id", nullable = false)
    private MissionTemplate template;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    private MatchMissionAssignment(
            Long matchId,
            int step,
            MissionTemplate template,
            LocalDateTime assignedAt
    ) {
        this.matchId = matchId;
        this.step = step;
        this.template = template;
        this.assignedAt = assignedAt;
    }

    public static MatchMissionAssignment assign(
            Long matchId,
            int step,
            MissionTemplate template,
            LocalDateTime assignedAt
    ) {
        return new MatchMissionAssignment(matchId, step, template, assignedAt);
    }
}
