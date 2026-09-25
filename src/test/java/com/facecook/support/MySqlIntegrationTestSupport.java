package com.facecook.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * 실제 MySQL 8.4(운영 RDS와 같은 메이저·마이너 버전) 위에서 JPA·Flyway·트랜잭션 잠금을 검증하는 통합 테스트의 공통 기반.
 *
 * <p>전제조건: Docker가 실행 중이어야 한다. Docker가 없으면 컨테이너 기동에서 실패하고, 테스트를
 * 건너뛰지 않는다. 잠금·격리 수준을 검증하는 테스트가 조용히 빠진 채 통과하는 것을 막기 위해서다.</p>
 *
 * <p>부작용: 테스트 JVM 하나당 MySQL 컨테이너 하나를 처음 사용할 때 기동해서 모든 하위 클래스가
 * 공유하고, JVM이 끝나면 Testcontainers가 정리한다. 스키마는 운영과 같은 Flyway 마이그레이션으로
 * 만들고 Hibernate는 {@code validate}로 대조한다. 공유 컨테이너이므로 각 테스트는 자기가 만든 행만
 * 다루고 다른 테스트의 데이터에 의존하지 않는다.</p>
 *
 * <p>트랜잭션: 하위 클래스의 테스트 메서드는 바깥 트랜잭션 없이 실행된다({@code NOT_SUPPORTED}).
 * 동시성 테스트는 각 작업이 서비스의 {@code @Transactional} 경계를 그대로 타야 하고, 다른 스레드가
 * 커밋된 데이터를 읽어야 하기 때문이다. 테스트가 만든 데이터는 커밋되므로 스스로 정리한다.</p>
 *
 * <p>잠금 대기 확인: {@link #awaitLockWaiters}는 {@code performance_schema.data_lock_waits}에 있는 잠금 대기
 * 수를 root 연결로 직접 조회한다. 동시성 테스트가 {@code sleep}으로 "아마 기다리고 있을 것"이라고
 * 추측하지 않고, 두 번째 트랜잭션이 실제로 잠금에서 멈춘 것을 확인한 뒤 첫 트랜잭션을 커밋하게 한다.</p>
 *
 * <p>순서 고정: {@link #firstCommitsBeforeSecond}는 첫 명령을 커밋 전 상태로 붙잡아 두고 두 번째 명령이 잠금 대기에
 * 들어간 것을 확인한 뒤에 커밋한다. "먼저 커밋한 쪽이 이긴다"는 조합을 시간 추측 없이 결정적으로 만든다.</p>
 *
 * @see ConcurrentRunner
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class MySqlIntegrationTestSupport {

    // 교착·잠금 누락을 잡기 위한 상한이라 넉넉하게 둔다. 컴파일과 컨테이너 기동이 겹친 첫 실행에서도 정상 대기가 이 안에 끝난다.
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(30);

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("facecook")
            .withUsername("facecook")
            .withPassword("test-password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected PlatformTransactionManager transactionManager;

    /**
     * 첫 명령을 바깥 트랜잭션 안에서 실행해 잠금을 커밋 전까지 쥐게 한 뒤, 다른 스레드에서 두 번째 명령을 시작한다.
     * 두 번째 명령이 DB에서 잠금 대기(LOCK WAIT) 상태에 들어간 것을 확인한 뒤에야 첫 명령을 커밋한다. 잠금이
     * 기다리게 하지 못해 두 번째 명령이 커밋 전에 끝나면 그 결과를 원인으로 담아 바로 실패시키고, 커밋 뒤에 끝난 두 번째 명령의 예외를
     * 돌려준다(성공이면 null).
     */
    protected Throwable firstCommitsBeforeSecond(Runnable first, Runnable second) throws Exception {
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
                awaitLockWaiters(1, LOCK_WAIT_TIMEOUT, secondResult[0]::isDone);
                if (secondResult[0].isDone()) {
                    throw new AssertionError(
                            "두 번째 명령은 첫 명령이 커밋될 때까지 잠금으로 기다려야 하는데 먼저 끝났다",
                            resultOf(secondResult[0]));
                }
            });
            return secondResult[0].get(LOCK_WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("두 번째 명령이 커밋 뒤에도 끝나지 않았다", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 이미 끝난 두 번째 명령의 결과(예외, 성공이면 null)를 꺼낸다. 실패 메시지의 원인으로 붙인다. */
    private static Throwable resultOf(Future<Throwable> done) {
        try {
            return done.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return exception;
        } catch (ExecutionException exception) {
            return exception.getCause();
        }
    }

    /** {@link #awaitLockWaiters(int, Duration, BooleanSupplier)}에서 중간에 멈추는 조건이 없는 형태. */
    protected static void awaitLockWaiters(int expected, Duration timeout) {
        awaitLockWaiters(expected, timeout, () -> false);
    }

    /**
     * 잠금 대기가 {@code expected}개 이상이 될 때까지, 또는 {@code stopWaiting}이 true가 될 때까지 기다린다.
     *
     * <p>대기는 {@code performance_schema.data_lock_waits}(대기 중인 잠금 요청과 그것을 막는 트랜잭션의 쌍)로 센다.
     * {@code information_schema.innodb_trx}의 {@code LOCK WAIT}는 쓰지 않는다 — 두 번째 트랜잭션이 행 잠금에서
     * 기다리는데도(쿼리 상태 {@code statistics}, {@code data_locks}에 {@code WAITING}) {@code innodb_trx} 목록에
     * 아예 나타나지 않는 경우가 로컬 전체 실행에서 재현됐다(#123).</p>
     *
     * <p>{@code stopWaiting}: 기다리던 쪽이 대기 없이 먼저 끝났는지 알려 준다. true가 되면 제한 시간을 다 쓰지 않고
     * 바로 돌아온다. 무엇이 잘못됐는지는 호출부가 그 결과로 판단한다.</p>
     *
     * <p>전제조건: 기다리는 트랜잭션이 이 컨테이너의 DB에서 실행 중이다. 테스트 사용자는 다른 연결의
     * 잠금을 볼 권한이 없어서 root 계정으로 별도 연결을 열어 조회한다.</p>
     *
     * <p>부작용: 없다(조회 전용, 매 호출마다 연결을 열고 닫는다).</p>
     *
     * <p>예외: 제한 시간 안에 조건이 충족되지 않으면 {@link AssertionError}.</p>
     */
    protected static void awaitLockWaiters(int expected, Duration timeout, BooleanSupplier stopWaiting) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            while (true) {
                try (ResultSet rows = statement.executeQuery(
                        "select count(*) from performance_schema.data_lock_waits")) {
                    rows.next();
                    if (rows.getInt(1) >= expected) {
                        return;
                    }
                }
                if (stopWaiting.getAsBoolean()) {
                    return;
                }
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("잠금 대기가 " + expected + "개 이상 되지 않았다: " + timeout);
                }
                Thread.sleep(20);
            }
        } catch (SQLException exception) {
            throw new AssertionError("잠금 대기 상태를 조회하지 못했다", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("잠금 대기 확인 중 인터럽트되었다", exception);
        }
    }
}
