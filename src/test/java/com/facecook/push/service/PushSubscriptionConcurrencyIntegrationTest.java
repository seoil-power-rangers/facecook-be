package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.support.ConcurrentRunner;
import com.facecook.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE #73 재현 사례를 실제 MySQL unique 제약으로 확인한다: 같은 userId+endpoint로 동시에
 * 두 구독 요청이 와도 둘 다 성공하고, 행이 정확히 하나만 남으며, 그 행의 키는 두 요청 중
 * 하나와 일치한다(값이 섞이거나 비어있지 않는다).
 *
 * <p>Mockito만으로는 이 경합(둘 다 "없음"으로 보고 각자 INSERT를 시도하다가 DB unique
 * 제약에서 실제로 충돌하는 것) 자체를 재현할 수 없다 — 그래서 실제 MySQL이 필요하다.</p>
 *
 * <p>부작용: 라운드마다 사용자 한 명을 커밋하고 끝나면 구독·사용자 행을 지운다.</p>
 */
@Import(PushSubscriptionService.class)
class PushSubscriptionConcurrencyIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @TestConfiguration
    static class VapidConfig {
        @Bean
        VapidProperties vapidProperties() {
            return new VapidProperties("test-public-key", "test-private-key");
        }
    }

    @Autowired
    private PushSubscriptionService pushSubscriptionService;

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
    void concurrentSubscribeRequestsForTheSameEndpointBothSucceedWithExactlyOneRow() {
        for (int round = 0; round < 15; round++) {
            long userId = insertUser();
            String endpoint = "https://push.example/" + UUID.randomUUID();
            PushSubscriptionRequest requestA =
                    new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys("p256dh-A", "auth-A"));
            PushSubscriptionRequest requestB =
                    new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys("p256dh-B", "auth-B"));

            List<Callable<Void>> tasks = List.of(
                    () -> {
                        pushSubscriptionService.subscribe(userId, requestA);
                        return null;
                    },
                    () -> {
                        pushSubscriptionService.subscribe(userId, requestB);
                        return null;
                    });
            List<ConcurrentRunner.Outcome<Void>> outcomes = ConcurrentRunner.runTogether(tasks, TIMEOUT);

            assertThat(outcomes).as("두 요청 모두 성공해야 한다").allMatch(ConcurrentRunner.Outcome::succeeded);

            Integer rowCount = jdbcTemplate.queryForObject(
                    "select count(*) from push_subscription where user_id = ? and endpoint = ?",
                    Integer.class, userId, endpoint);
            assertThat(rowCount).as("행이 하나만 남아야 한다").isEqualTo(1);

            var savedKeys = jdbcTemplate.queryForMap(
                    "select p256dh, auth from push_subscription where user_id = ? and endpoint = ?",
                    userId, endpoint);
            boolean matchesA = "p256dh-A".equals(savedKeys.get("p256dh")) && "auth-A".equals(savedKeys.get("auth"));
            boolean matchesB = "p256dh-B".equals(savedKeys.get("p256dh")) && "auth-B".equals(savedKeys.get("auth"));
            assertThat(matchesA || matchesB)
                    .as("최종 p256dh·auth는 같은 요청 한 쌍이어야 한다(둘이 섞이거나 비어있지 않음): " + savedKeys)
                    .isTrue();
        }
    }

    private long insertUser() {
        String email = "push-sub-" + UUID.randomUUID() + "@test.local";
        jdbcTemplate.update("insert into users (email) values (?)", email);
        Long id = jdbcTemplate.queryForObject("select user_id from users where email = ?", Long.class, email);
        userIds.add(id);
        return id;
    }
}
