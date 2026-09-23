package com.facecook.support;

import com.facecook.common.time.EventTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 연결 시간대 설정(application.yml의 {@code connection-init-sql})이 실제 MySQL에서 의도대로
 * 동작하는지 확인한다(#77).
 *
 * <p>확인 항목: (1) 풀에서 받는 연결마다 세션 시간대가 +09:00이다. (2) DB 기본값
 * ({@code CURRENT_TIMESTAMP})이 채우는 컬럼이 한국 시간으로 저장된다. (3) 애플리케이션이 넣는
 * {@code DATETIME} 값은 연결 시간대와 무관하게 넣은 그대로 읽힌다 — 이 설정이 기존 저장값이나
 * {@code EventTime}이 만든 값을 다시 9시간 옮기지 않는다는 뜻이다.</p>
 *
 * <p>부작용: 사용자·구독 행을 커밋하고 끝나면 지운다.</p>
 */
class DatabaseTimeZoneIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        userIds.forEach(id -> jdbcTemplate.update("delete from push_subscription where user_id = ?", id));
        userIds.forEach(id -> jdbcTemplate.update("delete from users where user_id = ?", id));
        userIds.clear();
    }

    @Test
    void everyPooledConnectionUsesKoreanSessionTimeZone() {
        assertThat(jdbcTemplate.queryForObject("select @@session.time_zone", String.class)).isEqualTo("+09:00");
    }

    @Test
    void columnsFilledByDatabaseDefaultAreStoredInKoreanTime() {
        long userId = insertUser();
        jdbcTemplate.update(
                "insert into push_subscription (user_id, endpoint, p256dh, auth) values (?, ?, ?, ?)",
                userId, "https://push.example/" + UUID.randomUUID(), "p", "a");

        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "select created_at from push_subscription where user_id = ?", LocalDateTime.class, userId);

        // UTC로 채워졌다면 한국 시간과 9시간 차이가 난다. 테스트 실행 시간을 감안해 몇 분 여유를 둔다.
        LocalDateTime koreanNow = EventTime.now(Clock.systemUTC());
        assertThat(Duration.between(createdAt, koreanNow).abs()).isLessThan(Duration.ofMinutes(2));
    }

    @Test
    void datetimeWrittenByTheApplicationIsReadBackUnchanged() {
        LocalDateTime written = LocalDateTime.of(2026, 9, 30, 12, 34, 56);
        long userId = insertUser();

        jdbcTemplate.update("update users set created_at = ? where user_id = ?", written, userId);

        assertThat(jdbcTemplate.queryForObject(
                "select created_at from users where user_id = ?", LocalDateTime.class, userId))
                .isEqualTo(written);
    }

    private long insertUser() {
        String email = "tz-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("insert into users (email) values (?)", email);
        Long id = jdbcTemplate.queryForObject("select user_id from users where email = ?", Long.class, email);
        userIds.add(id);
        return id;
    }
}
