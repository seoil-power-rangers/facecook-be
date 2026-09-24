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

/**
 * 매칭에 배정된 STEP별 미션 하나({@code match_mission_assignment}). 매칭당 STEP 1~3 세 행이고, 세 행 모두
 * 같은 묶음({@link MissionTemplate#getBundleId()})에서 나온다. 한 번 배정되면 바뀌지 않는다.
 *
 * <p>{@code @ManyToOne(fetch = LAZY)}: 미션 문구({@link MissionTemplate})는 필요할 때 읽는다. 목록 조회는
 * {@code MatchMissionAssignmentRepository}의 {@code @EntityGraph}로 함께 가져와 N+1을 막는다.</p>
 */
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
