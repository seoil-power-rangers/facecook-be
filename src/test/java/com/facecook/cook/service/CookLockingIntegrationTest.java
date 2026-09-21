package com.facecook.cook.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.dto.SendCookRequest;
import com.facecook.cook.entity.Cook;
import com.facecook.cook.repository.CookRepository;
import com.facecook.profile.service.ProfileActivityLookup;
import com.facecook.push.service.ParticipantPushNotificationService;
import com.facecook.support.ConcurrentRunner;
import com.facecook.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 콕의 취소·거절·전송이 같은 사용자 쌍에서 겹칠 때 잠금 규약(사용자 행 → 콕 행)이 실제 MySQL에서
 * 지켜지는지 확인한다.
 *
 * <p>확인 항목: 첫 명령이 커밋하기 전에는 두 번째 명령이 잠금으로 기다리고, 커밋 뒤에는 최신 상태를 보고
 * 판정하는지(여섯 가지 경합), 실제 동시 실행에서도 승자가 하나뿐이고 교착이 없는지. 두 번째 명령은
 * 첫 명령이 커밋하기 전에 쌍 조회(일반 읽기)를 이미 마친 상태에서 잠금을 기다리므로, "잠금 전에 읽은
 * 상태로 판정하지 않는다"는 조건도 함께 검증된다.</p>
 *
 * <p>부작용: 테스트마다 사용자 두 명과 콕·매칭 행을 커밋하고 끝나면 삭제한다. 푸시는 목으로 대체한다.
 * 사용자 ID 순서(보내는 사람이 작은 경우와 큰 경우)를 모두 돌려 잠금 순서가 방향과 무관한지 본다.</p>
 */
@Import(CookService.class)
class CookLockingIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-21T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @MockitoBean
    private ParticipantPushNotificationService pushNotificationService;

    @MockitoBean
    private ProfileActivityLookup activityLookup;

    @Autowired
    private CookService cookService;

    @Autowired
    private CookRepository cookRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // cook.match_id가 match_info를 참조하므로 콕을 먼저 지운다.
        for (Long userId : userIds) {
            jdbcTemplate.update("delete from cook where sender_id = ? or receiver_id = ?", userId, userId);
        }
        for (Long userId : userIds) {
            jdbcTemplate.update("delete from match_info where user_a_id = ? or user_b_id = ?", userId, userId);
        }
        userIds.forEach(id -> jdbcTemplate.update("delete from users where user_id = ?", id));
        userIds.clear();
    }

    @ParameterizedTest(name = "보낸 사람 ID가 {0} 때")
    @ValueSource(booleans = {true, false})
    void cancelFirstThenRejectSeesCancelledCook(boolean senderHasLowerId) throws Exception {
        Pair pair = pair(senderHasLowerId);
        long cookId = pendingCook(pair.sender(), pair.receiver());

        Throwable second = firstCommitsBeforeSecond(
                () -> cookService.cancel(pair.sender(), cookId),
                () -> cookService.reject(pair.receiver(), cookId));

        assertThat(status(cookId)).isEqualTo("cancelled");
        assertErrorCode(second, ErrorCode.NOT_FOUND);
    }

    @ParameterizedTest(name = "보낸 사람 ID가 {0} 때")
    @ValueSource(booleans = {true, false})
    void rejectFirstThenCancelSeesRejectedCook(boolean senderHasLowerId) throws Exception {
        Pair pair = pair(senderHasLowerId);
        long cookId = pendingCook(pair.sender(), pair.receiver());

        Throwable second = firstCommitsBeforeSecond(
                () -> cookService.reject(pair.receiver(), cookId),
                () -> cookService.cancel(pair.sender(), cookId));

        assertThat(status(cookId)).isEqualTo("rejected");
        assertErrorCode(second, ErrorCode.ALREADY_REJECTED);
    }

    @ParameterizedTest(name = "보낸 사람 ID가 {0} 때")
    @ValueSource(booleans = {true, false})
    void reverseSendFirstThenRejectSeesMatchedCook(boolean senderHasLowerId) throws Exception {
        Pair pair = pair(senderHasLowerId);
        long cookId = pendingCook(pair.sender(), pair.receiver());

        Throwable second = firstCommitsBeforeSecond(
                () -> cookService.send(pair.receiver(), new SendCookRequest(pair.sender())),
                () -> cookService.reject(pair.receiver(), cookId));

        assertThat(status(cookId)).isEqualTo("matched");
        assertThat(statusBetween(pair.receiver(), pair.sender())).isEqualTo("matched");
        assertErrorCode(second, ErrorCode.ALREADY_MATCHED);
    }

    @ParameterizedTest(name = "보낸 사람 ID가 {0} 때")
    @ValueSource(booleans = {true, false})
    void rejectFirstThenReverseSendIsBlockedAndCreatesNothing(boolean senderHasLowerId) throws Exception {
        Pair pair = pair(senderHasLowerId);
        long cookId = pendingCook(pair.sender(), pair.receiver());

        Throwable second = firstCommitsBeforeSecond(
                () -> cookService.reject(pair.receiver(), cookId),
                () -> cookService.send(pair.receiver(), new SendCookRequest(pair.sender())));

        assertThat(status(cookId)).isEqualTo("rejected");
        assertThat(statusBetween(pair.receiver(), pair.sender())).isNull();
        assertThat(matchCount(pair)).isZero();
        assertErrorCode(second, ErrorCode.ALREADY_REJECTED);
    }

    @ParameterizedTest(name = "보낸 사람 ID가 {0} 때")
    @ValueSource(booleans = {true, false})
    void cancelFirstThenReverseSendCreatesPendingCookWithoutMatch(boolean senderHasLowerId) throws Exception {
        Pair pair = pair(senderHasLowerId);
        long cookId = pendingCook(pair.sender(), pair.receiver());

        Throwable second = firstCommitsBeforeSecond(
                () -> cookService.cancel(pair.sender(), cookId),
                () -> cookService.send(pair.receiver(), new SendCookRequest(pair.sender())));

        assertThat(second).isNull();
        assertThat(status(cookId)).isEqualTo("cancelled");
        assertThat(statusBetween(pair.receiver(), pair.sender())).isEqualTo("pending");
        assertThat(matchCount(pair)).isZero();
    }

    @ParameterizedTest(name = "보낸 사람 ID가 {0} 때")
    @ValueSource(booleans = {true, false})
    void reverseSendFirstThenCancelSeesMatchedCook(boolean senderHasLowerId) throws Exception {
        Pair pair = pair(senderHasLowerId);
        long cookId = pendingCook(pair.sender(), pair.receiver());

        Throwable second = firstCommitsBeforeSecond(
                () -> cookService.send(pair.receiver(), new SendCookRequest(pair.sender())),
                () -> cookService.cancel(pair.sender(), cookId));

        assertThat(status(cookId)).isEqualTo("matched");
        assertThat(matchCount(pair)).isEqualTo(1);
        assertErrorCode(second, ErrorCode.ALREADY_MATCHED);
    }

    @ParameterizedTest(name = "보낸 사람 ID가 {0} 때")
    @ValueSource(booleans = {true, false})
    void concurrentCancelAndRejectLeaveExactlyOneWinnerWithoutDeadlock(boolean senderHasLowerId) {
        for (int round = 0; round < 15; round++) {
            Pair pair = pair(senderHasLowerId);
            long cookId = pendingCook(pair.sender(), pair.receiver());

            List<Callable<Void>> tasks = List.of(
                    () -> {
                        cookService.cancel(pair.sender(), cookId);
                        return null;
                    },
                    () -> {
                        cookService.reject(pair.receiver(), cookId);
                        return null;
                    });
            List<ConcurrentRunner.Outcome<Void>> outcomes = ConcurrentRunner.runTogether(tasks, TIMEOUT);

            ConcurrentRunner.Outcome<Void> cancel = outcomes.get(0);
            ConcurrentRunner.Outcome<Void> reject = outcomes.get(1);
            String finalStatus = status(cookId);
            if ("cancelled".equals(finalStatus)) {
                assertThat(cancel.succeeded()).as("cancel이 이겼다").isTrue();
                assertErrorCode(reject.error(), ErrorCode.NOT_FOUND);
            } else {
                assertThat(finalStatus).isEqualTo("rejected");
                assertThat(reject.succeeded()).as("reject가 이겼다").isTrue();
                assertErrorCode(cancel.error(), ErrorCode.ALREADY_REJECTED);
            }
        }
    }

    @Test
    void repeatedRejectIsIdempotentUnderConcurrency() {
        Pair pair = pair(true);
        long cookId = pendingCook(pair.sender(), pair.receiver());

        List<Callable<Void>> tasks = List.of(
                () -> {
                    cookService.reject(pair.receiver(), cookId);
                    return null;
                },
                () -> {
                    cookService.reject(pair.receiver(), cookId);
                    return null;
                });
        List<ConcurrentRunner.Outcome<Void>> outcomes = ConcurrentRunner.runTogether(tasks, TIMEOUT);

        assertThat(outcomes).allMatch(ConcurrentRunner.Outcome::succeeded);
        assertThat(status(cookId)).isEqualTo("rejected");
    }

    /**
     * 첫 명령을 바깥 트랜잭션 안에서 실행해 잠금을 커밋 전까지 쥐게 한 뒤, 다른 스레드에서 두 번째 명령을 시작한다.
     * 두 번째 명령이 커밋 전에 끝나면(잠금이 기다리게 하지 못하면) 실패시키고, 커밋 뒤에 끝난 두 번째 명령의
     * 예외를 돌려준다(성공이면 null).
     */
    private Throwable firstCommitsBeforeSecond(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            @SuppressWarnings("unchecked")
            Future<Throwable>[] secondResult = new Future[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                first.run();
                secondResult[0] = executor.submit(() -> {
                    try {
                        second.run();
                        return null;
                    } catch (Throwable throwable) {
                        return throwable;
                    }
                });
                sleep(500);
                assertThat(secondResult[0].isDone())
                        .as("두 번째 명령은 첫 명령이 커밋될 때까지 잠금으로 기다려야 한다")
                        .isFalse();
            });
            return secondResult[0].get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("두 번째 명령이 커밋 뒤에도 끝나지 않았다", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static void assertErrorCode(Throwable actual, ErrorCode expected) {
        assertThat(actual).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private Pair pair(boolean senderHasLowerId) {
        long first = insertUser();
        long second = insertUser();
        return senderHasLowerId ? new Pair(first, second) : new Pair(second, first);
    }

    private long insertUser() {
        String email = "cook-lock-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("insert into users (email) values (?)", email);
        Long id = jdbcTemplate.queryForObject("select user_id from users where email = ?", Long.class, email);
        userIds.add(id);
        return id;
    }

    private long pendingCook(long senderId, long receiverId) {
        Cook saved = cookRepository.save(Cook.pending(senderId, receiverId, LocalDateTime.of(2026, 9, 21, 11, 0)));
        return saved.getId();
    }

    private String status(long cookId) {
        return jdbcTemplate.queryForObject("select status from cook where cook_id = ?", String.class, cookId);
    }

    private String statusBetween(long senderId, long receiverId) {
        List<String> statuses = jdbcTemplate.queryForList(
                "select status from cook where sender_id = ? and receiver_id = ?", String.class, senderId, receiverId);
        return statuses.isEmpty() ? null : statuses.get(0);
    }

    private int matchCount(Pair pair) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from match_info where (user_a_id = ? and user_b_id = ?) or (user_a_id = ? and user_b_id = ?)",
                Integer.class, pair.sender(), pair.receiver(), pair.receiver(), pair.sender());
        return count == null ? 0 : count;
    }

    private record Pair(long sender, long receiver) {
    }
}
