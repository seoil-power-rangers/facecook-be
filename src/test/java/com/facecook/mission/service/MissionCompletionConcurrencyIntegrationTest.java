package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BE #88 재현 사례를 실제 MySQL 행 잠금으로 확인한다: 같은 매칭·같은 STEP을 동시에 완료
 * 처리하려는 관리자 요청 두 개 중 정확히 하나만 성공하고, 나머지 하나는 {@code
 * MISSION_STEP_MISMATCH}(409)로 거절되며 아무것도 남기지 않는다.
 *
 * <p>확인 항목: (1) 첫 요청이 커밋되기 전까지 행을 붙잡고 있을 때 두 번째 요청이 그 행 잠금에서
 * 실제로 기다리는 것을 DB에서 확인한 뒤 첫 요청을 커밋한다(모킹된 저장소로는 이 잠금 경합 자체를
 * 재현할 수 없다). (2) 같은 STEP을 재시도해도 두 번째 호출이 성공으로 처리되지 않는다(멱등 성공으로
 * 두지 않는다). (3) 실제 동시 실행을 반복해도 항상 승자가 정확히 하나이고 STEP이 한 번만 전진한다.</p>
 *
 * <p>부작용: 테스트마다 사용자 두 명과 매칭 행 하나를 커밋하고 끝나면 삭제한다. 미션 템플릿은
 * 마이그레이션(V6)으로 이미 채워져 있으므로 여기서 만들지 않는다.</p>
 */
@Import({MissionService.class, MissionAssignmentService.class, MissionAssignmentWriter.class,
        MissionAuthorizationService.class})
class MissionCompletionConcurrencyIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-30T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MissionService missionService;

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
    void secondConcurrentRequestForTheSameStepIsRejectedAfterTheFirstCommits() throws Exception {
        Match match = newMatchAtStep(1);

        Throwable secondError = firstCommitsBeforeSecond(
                () -> missionService.completeCurrentStep(match.matchId(), match.adminA(), 1),
                () -> missionService.completeCurrentStep(match.matchId(), match.adminB(), 1));

        assertThat(secondError).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STEP_MISMATCH));
        assertThat(currentStep(match.matchId())).isEqualTo(2);
        assertThat(step1CompletedBy(match.matchId())).isEqualTo(match.adminA());
    }

    @Test
    void retryingAnAlreadyCompletedStepIsRejectedNotTreatedAsSuccess() {
        Match match = newMatchAtStep(1);
        missionService.completeCurrentStep(match.matchId(), match.adminA(), 1);

        assertThatThrownBy(() -> missionService.completeCurrentStep(match.matchId(), match.adminA(), 1))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MISSION_STEP_MISMATCH));
        assertThat(currentStep(match.matchId())).isEqualTo(2);
    }

    @Test
    void repeatedRealConcurrentCompletionRequestsAlwaysLeaveExactlyOneWinner() {
        for (int round = 0; round < 15; round++) {
            Match match = newMatchAtStep(1);

            List<Callable<Boolean>> tasks = List.of(
                    () -> completesOrRejected(match.matchId(), match.adminA()),
                    () -> completesOrRejected(match.matchId(), match.adminB()));
            List<ConcurrentRunner.Outcome<Boolean>> outcomes = ConcurrentRunner.runTogether(tasks, TIMEOUT);

            assertThat(outcomes).as("MISSION_STEP_MISMATCH 외의 예외 없이 둘 다 끝나야 한다")
                    .allMatch(ConcurrentRunner.Outcome::succeeded);
            long winners = outcomes.stream().filter(outcome -> Boolean.TRUE.equals(outcome.value())).count();
            assertThat(winners).as("정확히 한 요청만 성공해야 한다").isEqualTo(1);
            assertThat(currentStep(match.matchId())).as("STEP은 한 번만 전진해야 한다").isEqualTo(2);
        }
    }

    /** 완료에 성공하면 true, {@code MISSION_STEP_MISMATCH}로 거절되면 false. 그 외 예외는 그대로 던진다. */
    private boolean completesOrRejected(long matchId, long adminId) {
        try {
            missionService.completeCurrentStep(matchId, adminId, 1);
            return true;
        } catch (ApiException exception) {
            if (exception.getErrorCode() == ErrorCode.MISSION_STEP_MISMATCH) {
                return false;
            }
            throw exception;
        }
    }

    private int currentStep(long matchId) {
        return jdbcTemplate.queryForObject(
                "select current_step from match_info where match_id = ?", Integer.class, matchId);
    }

    private Long step1CompletedBy(long matchId) {
        return jdbcTemplate.queryForObject(
                "select step1_completed_by from match_info where match_id = ?", Long.class, matchId);
    }

    /**
     * 매칭 하나와, 완료 처리를 요청할 관리자 역할의 사용자 둘을 함께 만든다. {@code
     * step1_completed_by}가 {@code users}를 참조하는 FK라서 실제로 존재하는 사용자 id가
     * 필요하다 — 매칭 당사자와는 별개로 둔다(관리자 권한 검사는 이 테스트의 관심사가 아니다).
     */
    private Match newMatchAtStep(int currentStep) {
        long first = insertUser();
        long second = insertUser();
        long userA = Math.min(first, second);
        long userB = Math.max(first, second);
        jdbcTemplate.update(
                "insert into match_info (user_a_id, user_b_id, matched_at, current_step) values (?, ?, ?, ?)",
                userA, userB, LocalDateTime.of(2026, 9, 30, 10, 0), currentStep);
        Long matchId = jdbcTemplate.queryForObject(
                "select match_id from match_info where user_a_id = ? and user_b_id = ?", Long.class, userA, userB);
        matchIds.add(matchId);
        return new Match(matchId, insertUser(), insertUser());
    }

    private long insertUser() {
        String email = "mission-complete-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("insert into users (email) values (?)", email);
        Long id = jdbcTemplate.queryForObject("select user_id from users where email = ?", Long.class, email);
        userIds.add(id);
        return id;
    }

    private record Match(long matchId, long adminA, long adminB) {
    }
}
