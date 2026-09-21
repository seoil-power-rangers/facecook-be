package com.facecook.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

/**
 * 실제 MySQL 8.0 위에서 JPA·Flyway·트랜잭션 잠금을 검증하는 통합 테스트의 공통 기반.
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
 * <p>잠금 대기 확인: {@link #awaitLockWaiters}는 InnoDB가 "잠금을 기다리는 중"(LOCK WAIT)으로 보고하는
 * 트랜잭션 수를 root 연결로 직접 조회한다. 동시성 테스트가 {@code sleep}으로 "아마 기다리고 있을 것"이라고
 * 추측하지 않고, 두 번째 트랜잭션이 실제로 잠금에서 멈춘 것을 확인한 뒤 첫 트랜잭션을 커밋하게 한다.</p>
 *
 * @see ConcurrentRunner
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public abstract class MySqlIntegrationTestSupport {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
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

    /**
     * 잠금을 기다리는 InnoDB 트랜잭션이 {@code expected}개 이상이 될 때까지 기다린다.
     *
     * <p>전제조건: 기다리는 트랜잭션이 이 컨테이너의 DB에서 실행 중이다. 테스트 사용자는 다른 연결의
     * 트랜잭션을 볼 권한(PROCESS)이 없어서 root 계정으로 별도 연결을 열어 조회한다.</p>
     *
     * <p>부작용: 없다(조회 전용, 매 호출마다 연결을 열고 닫는다).</p>
     *
     * <p>예외: 제한 시간 안에 조건이 충족되지 않으면 {@link AssertionError}.</p>
     */
    protected static void awaitLockWaiters(int expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            while (true) {
                try (ResultSet rows = statement.executeQuery(
                        "select count(*) from information_schema.innodb_trx where trx_state = 'LOCK WAIT'")) {
                    rows.next();
                    if (rows.getInt(1) >= expected) {
                        return;
                    }
                }
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("잠금을 기다리는 트랜잭션이 " + expected + "개 이상 되지 않았다: " + timeout);
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
