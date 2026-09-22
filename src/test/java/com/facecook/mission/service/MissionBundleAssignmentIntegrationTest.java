package com.facecook.mission.service;

import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.support.ConcurrentRunner;
import com.facecook.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미션 STEP1~3이 실제 마이그레이션(V6)으로 채워진 묶음 중 하나에서 같이 배정되는지, 동시에 배정을
 * 시도해도 매칭 하나에는 묶음 하나만 쓰이는지 확인한다.
 *
 * <p>확인 항목: 한 매칭의 STEP1·2·3이 항상 같은 {@code bundle_id}를 가리키는지, 배정된 문구가 실제
 * 마이그레이션 데이터(246행)와 일치하는지, 같은 매칭에 동시에 배정을 시도해도 정확히 3행만 저장되고
 * 묶음이 하나로 고정되는지.</p>
 *
 * <p>부작용: 테스트마다 사용자 두 명과 매칭(match_info) 행 하나를 커밋하고 끝나면 삭제한다. 미션 템플릿은
 * 마이그레이션으로 이미 채워져 있으므로 여기서 만들지 않는다.</p>
 */
@Import({MissionAssignmentService.class, MissionAssignmentWriter.class})
class MissionBundleAssignmentIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-30T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MissionAssignmentService assignmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> matchIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long matchId : matchIds) {
            jdbcTemplate.update("delete from match_mission_assignment where match_id = ?", matchId);
            jdbcTemplate.update("delete from match_info where match_id = ?", matchId);
        }
        userIds.forEach(id -> jdbcTemplate.update("delete from users where user_id = ?", id));
        matchIds.clear();
        userIds.clear();
    }

    @Test
    void assignsAllThreeStepsFromTheSameBundleAndMatchesMigrationContent() {
        long matchId = newMatch();

        List<MatchMissionAssignment> assignments = assignmentService.assignIfAbsent(matchId);

        assertThat(assignments).extracting(MatchMissionAssignment::getStep).containsExactly(1, 2, 3);
        Set<Long> bundleIds = assignments.stream()
                .map(a -> a.getTemplate().getBundleId())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(bundleIds).as("STEP1~3이 모두 같은 묶음이어야 한다").hasSize(1);

        long bundleId = bundleIds.iterator().next();
        for (MatchMissionAssignment assignment : assignments) {
            String expectedContent = jdbcTemplate.queryForObject(
                    "select content from mission_template where bundle_id = ? and step = ?",
                    String.class, bundleId, assignment.getStep());
            assertThat(assignment.getTemplate().getContent()).isEqualTo(expectedContent);
        }
    }

    @Test
    void concurrentAssignmentAttemptsOnTheSameNewMatchStillUseExactlyOneBundle() {
        for (int round = 0; round < 15; round++) {
            long matchId = newMatch();

            List<Callable<List<MatchMissionAssignment>>> tasks = List.of(
                    () -> assignmentService.assignIfAbsent(matchId),
                    () -> assignmentService.assignIfAbsent(matchId));
            List<ConcurrentRunner.Outcome<List<MatchMissionAssignment>>> outcomes =
                    ConcurrentRunner.runTogether(tasks, TIMEOUT);

            assertThat(outcomes).allMatch(ConcurrentRunner.Outcome::succeeded);

            Integer rowCount = jdbcTemplate.queryForObject(
                    "select count(*) from match_mission_assignment where match_id = ?", Integer.class, matchId);
            assertThat(rowCount).as("묶음 하나(3행)만 저장돼야 한다").isEqualTo(3);

            List<Long> bundleIds = jdbcTemplate.queryForList(
                    """
                    select distinct mt.bundle_id
                    from match_mission_assignment a
                    join mission_template mt on mt.mission_template_id = a.mission_template_id
                    where a.match_id = ?
                    """,
                    Long.class, matchId);
            assertThat(bundleIds).as("두 응답 모두 같은 묶음을 가리켜야 한다").hasSize(1);
        }
    }

    private long newMatch() {
        long first = insertUser();
        long second = insertUser();
        long userA = Math.min(first, second);
        long userB = Math.max(first, second);
        jdbcTemplate.update(
                "insert into match_info (user_a_id, user_b_id, matched_at) values (?, ?, ?)",
                userA, userB, LocalDateTime.of(2026, 9, 30, 10, 0));
        Long matchId = jdbcTemplate.queryForObject(
                "select match_id from match_info where user_a_id = ? and user_b_id = ?", Long.class, userA, userB);
        matchIds.add(matchId);
        return matchId;
    }

    private long insertUser() {
        String email = "mission-bundle-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("insert into users (email) values (?)", email);
        Long id = jdbcTemplate.queryForObject("select user_id from users where email = ?", Long.class, email);
        userIds.add(id);
        return id;
    }
}
