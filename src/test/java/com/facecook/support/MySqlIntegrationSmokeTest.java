package com.facecook.support;

import com.facecook.cook.repository.CookUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL 통합 테스트 기반이 동시성 테스트에 필요한 조건을 실제로 갖췄는지 확인한다.
 *
 * <p>확인 항목: 운영과 같은 Flyway 마이그레이션이 적용되고 Hibernate {@code validate}가 통과하는지,
 * 격리 수준이 운영과 같은 REPEATABLE-READ인지, 행 잠금이 다른 트랜잭션을 실제로 기다리게 하는지,
 * {@link ConcurrentRunner}가 성공과 예외를 모두 회수하는지.</p>
 *
 * <p>부작용: 테스트마다 {@code users} 행을 하나 커밋하고 끝나면 삭제한다.</p>
 */
class MySqlIntegrationSmokeTest extends MySqlIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CookUserRepository cookUserRepository;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedUsers() {
        createdUserIds.forEach(id -> jdbcTemplate.update("delete from users where user_id = ?", id));
        createdUserIds.clear();
    }

    @Test
    void appliesFlywayMigrationsAndUsesRepeatableRead() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 1", Integer.class);
        String isolation = jdbcTemplate.queryForObject("select @@transaction_isolation", String.class);

        assertThat(applied).isGreaterThanOrEqualTo(4);
        assertThat(isolation).isEqualTo("REPEATABLE-READ");
    }

    @Test
    void rowLockMakesSecondTransactionWaitUntilFirstCommits() throws Exception {
        long userId = insertUser();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch holderLocked = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
                cookUserRepository.findAllByIdForUpdate(List.of(userId));
                holderLocked.countDown();
                awaitUninterruptibly(releaseHolder);
            }));
            assertThat(holderLocked.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();

            Future<Integer> waiter = executor.submit(() -> transaction.execute(
                    status -> cookUserRepository.findAllByIdForUpdate(List.of(userId)).size()));

            awaitLockWaiters(1, TIMEOUT);
            assertThat(waiter.isDone()).as("첫 트랜잭션이 잠근 행을 두 번째가 기다려야 한다").isFalse();

            releaseHolder.countDown();
            holder.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(waiter.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            releaseHolder.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentRunnerCollectsSuccessAndFailure() {
        List<Callable<String>> tasks = List.of(
                () -> "ok",
                () -> {
                    throw new IllegalStateException("boom");
                });

        List<ConcurrentRunner.Outcome<String>> outcomes = ConcurrentRunner.runTogether(tasks, TIMEOUT);

        assertThat(outcomes.get(0).succeeded()).isTrue();
        assertThat(outcomes.get(0).value()).isEqualTo("ok");
        assertThat(outcomes.get(1).succeeded()).isFalse();
        assertThat(outcomes.get(1).error()).isInstanceOf(IllegalStateException.class);
    }

    private long insertUser() {
        String email = "smoke-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("insert into users (email) values (?)", email);
        Long userId = jdbcTemplate.queryForObject(
                "select user_id from users where email = ?", Long.class, email);
        createdUserIds.add(userId);
        return userId;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
