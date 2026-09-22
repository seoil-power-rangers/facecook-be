package com.facecook.match.service;

import com.facecook.match.repository.MatchInfoRepository;
import com.facecook.profile.service.ProfileActivityLookup;
import com.facecook.support.ConcurrentRunner;
import com.facecook.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

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

/**
 * 읽음 처리가 두 참가자의 읽음 시각을 서로 덮어쓰지 않고, 같은 참가자의 시각을 뒤로 물러나게 하지 않는지
 * 실제 MySQL로 확인한다.
 *
 * <p>확인 항목: (1) 두 참가자가 동시에 읽음 처리해도 두 컬럼이 모두 기록된다 — 첫 요청이 커밋 전까지 행을
 * 붙잡고 있을 때 두 번째 요청이 그 행 잠금에서 기다리는 것을 DB에서 확인한 뒤 커밋한다(엔티티를 고쳐 저장하던
 * 방식은 커밋 때에야 UPDATE해서 기다리지 않고, 행 전체를 써서 상대 컬럼을 덮을 수 있었다). (2) 더 늦은 시각이
 * 먼저 커밋된 뒤 더 이른 시각의 요청이 뒤늦게 커밋돼도 시각이 뒤로 가지 않는다. (3) 값이 없던 컬럼은 처음
 * 기록된다. (4) 실제 동시 실행을 반복해도 두 컬럼이 항상 남는다.</p>
 *
 * <p>부작용: 테스트마다 사용자 두 명과 매칭 행 하나를 커밋하고 끝나면 삭제한다.</p>
 */
@Import(MatchService.class)
class MatchReadStateIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final LocalDateTime MATCHED_AT = LocalDateTime.of(2026, 9, 30, 10, 0);
    private static final LocalDateTime CLOCK_NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            // 한국 시간 2026-09-30 12:00
            return Clock.fixed(Instant.parse("2026-09-30T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @MockitoBean
    private ProfileActivityLookup activityLookup;

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchInfoRepository matchInfoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> matchIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        matchIds.forEach(id -> jdbcTemplate.update("delete from match_info where match_id = ?", id));
        userIds.forEach(id -> jdbcTemplate.update("delete from users where user_id = ?", id));
        matchIds.clear();
        userIds.clear();
    }

    @Test
    void bothParticipantsReadingAtOnceKeepBothLastReadTimes() throws Exception {
        Match match = newMatch();

        Throwable secondError = firstCommitsBeforeSecond(
                () -> matchService.markRead(match.userA(), match.matchId()),
                () -> matchService.markRead(match.userB(), match.matchId()));

        assertThat(secondError).isNull();
        assertThat(lastReadA(match)).isEqualTo(CLOCK_NOW);
        assertThat(lastReadB(match)).isEqualTo(CLOCK_NOW);
    }

    @Test
    void theOtherSideReadFirstIsNotOverwrittenWhenTheSecondReadCommits() throws Exception {
        Match match = newMatch();

        Throwable secondError = firstCommitsBeforeSecond(
                () -> matchService.markRead(match.userB(), match.matchId()),
                () -> matchService.markRead(match.userA(), match.matchId()));

        assertThat(secondError).isNull();
        assertThat(lastReadA(match)).isEqualTo(CLOCK_NOW);
        assertThat(lastReadB(match)).isEqualTo(CLOCK_NOW);
    }

    @Test
    void anEarlierReadCommittedLaterDoesNotMoveTheTimeBackwards() {
        Match match = newMatch();
        LocalDateTime later = CLOCK_NOW.plusMinutes(5);
        LocalDateTime earlier = CLOCK_NOW.plusMinutes(1);

        int firstUpdated = markReadAsUserA(match, later);
        int reversedUpdated = markReadAsUserA(match, earlier);

        assertThat(firstUpdated).isEqualTo(1);
        assertThat(reversedUpdated).as("더 이른 시각은 기록하지 않는다").isZero();
        assertThat(lastReadA(match)).isEqualTo(later);
    }

    @Test
    void anEarlierReadCommittedLaterDoesNotMoveTheBSideBackwardsEither() {
        Match match = newMatch();
        LocalDateTime later = CLOCK_NOW.plusMinutes(5);

        markReadAsUserB(match, later);
        int reversedUpdated = markReadAsUserB(match, CLOCK_NOW.plusMinutes(1));

        assertThat(reversedUpdated).isZero();
        assertThat(lastReadB(match)).isEqualTo(later);
    }

    @Test
    void aLastReadTimeThatWasNeverSetIsRecordedAndTheOtherColumnStaysEmpty() {
        Match match = newMatch();

        matchService.markRead(match.userA(), match.matchId());

        assertThat(lastReadA(match)).isEqualTo(CLOCK_NOW);
        assertThat(lastReadB(match)).isNull();
    }

    @Test
    void repeatedConcurrentReadsFromBothSidesAlwaysKeepBothTimes() {
        for (int round = 0; round < 20; round++) {
            Match match = newMatch();

            List<Callable<Void>> tasks = List.of(
                    () -> {
                        matchService.markRead(match.userA(), match.matchId());
                        return null;
                    },
                    () -> {
                        matchService.markRead(match.userB(), match.matchId());
                        return null;
                    });
            List<ConcurrentRunner.Outcome<Void>> outcomes = ConcurrentRunner.runTogether(tasks, TIMEOUT);

            assertThat(outcomes).allMatch(ConcurrentRunner.Outcome::succeeded);
            assertThat(lastReadA(match)).isEqualTo(CLOCK_NOW);
            assertThat(lastReadB(match)).isEqualTo(CLOCK_NOW);
        }
    }

    private int markReadAsUserA(Match match, LocalDateTime readAt) {
        Integer updated = new TransactionTemplate(transactionManager)
                .execute(status -> matchInfoRepository.markReadAsUserA(match.matchId(), readAt));
        return updated == null ? 0 : updated;
    }

    private int markReadAsUserB(Match match, LocalDateTime readAt) {
        Integer updated = new TransactionTemplate(transactionManager)
                .execute(status -> matchInfoRepository.markReadAsUserB(match.matchId(), readAt));
        return updated == null ? 0 : updated;
    }

    private LocalDateTime lastReadA(Match match) {
        return jdbcTemplate.queryForObject(
                "select user_a_last_read_at from match_info where match_id = ?", LocalDateTime.class, match.matchId());
    }

    private LocalDateTime lastReadB(Match match) {
        return jdbcTemplate.queryForObject(
                "select user_b_last_read_at from match_info where match_id = ?", LocalDateTime.class, match.matchId());
    }

    /** 두 사용자와 그 사이의 매칭을 만든다. A쪽이 더 작은 userId다(서비스가 그렇게 저장한다). */
    private Match newMatch() {
        long first = insertUser();
        long second = insertUser();
        long userA = Math.min(first, second);
        long userB = Math.max(first, second);
        jdbcTemplate.update(
                "insert into match_info (user_a_id, user_b_id, matched_at) values (?, ?, ?)", userA, userB, MATCHED_AT);
        Long matchId = jdbcTemplate.queryForObject(
                "select match_id from match_info where user_a_id = ? and user_b_id = ?", Long.class, userA, userB);
        matchIds.add(matchId);
        return new Match(matchId, userA, userB);
    }

    private long insertUser() {
        String email = "read-state-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("insert into users (email) values (?)", email);
        Long id = jdbcTemplate.queryForObject("select user_id from users where email = ?", Long.class, email);
        userIds.add(id);
        return id;
    }

    private record Match(long matchId, long userA, long userB) {
    }
}
