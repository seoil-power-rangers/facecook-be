package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.push.repository.PushSubscriptionRepository;
import com.facecook.support.ConcurrentRunner;
import com.facecook.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE #73 재현 사례를 실제 MySQL unique 제약으로, 그것도 우연이 아니라 결정적으로 확인한다.
 *
 * <p>단순히 {@link ConcurrentRunner#runTogether}로 두 스레드를 "동시에 출발"시키는 것만으로는
 * 부족하다 — 스레드 스케줄링에 따라 한쪽이 조회·INSERT·커밋까지 전부 끝낸 뒤에야 다른 쪽이
 * 조회를 시작할 수도 있고, 그러면 애초에 unique 충돌 자체가 안 나서 복구 코드를 지워도 이
 * 테스트가 통과해버린다(실제로 확인함 — 아래 "결정성 증명" 참고). 그래서 실제 저장소를 감싸는
 * {@link CyclicBarrier} 프록시로 "두 스레드 모두 첫 조회에서 '없음'을 확인한 뒤에만" 두
 * INSERT가 동시에 진행되도록 강제한다.</p>
 *
 * <p>결정성 증명: {@code PushSubscriptionService.subscribe}를 원래 버그 있던 버전(재조회
 * 복구 없이 {@code @Transactional}만 건)으로 되돌리고 이 테스트를 돌리면, barrier가 두
 * 조회를 정확히 맞춰 놓았기 때문에 매번 예외 없이 100% 실패한다(우연이 아님). 수정된 코드로는
 * 매번 성공한다.</p>
 *
 * <p>부작용: 라운드마다 사용자 한 명을 커밋하고 끝나면 구독·사용자 행을 지운다.</p>
 */
class PushSubscriptionConcurrencyIntegrationTest extends MySqlIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final VapidProperties VAPID_PROPERTIES = new VapidProperties("test-public-key", "test-private-key");

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

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
        for (int round = 0; round < 5; round++) {
            long userId = insertUser();
            String endpoint = "https://push.example/" + UUID.randomUUID();
            PushSubscriptionRequest requestA =
                    new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys("p256dh-A", "auth-A"));
            PushSubscriptionRequest requestB =
                    new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys("p256dh-B", "auth-B"));

            // 두 스레드가 각각 자기만의 findByUserIdAndEndpoint 호출 횟수를 세도록 별도
            // 인스턴스를 준다 — barrier(parties=2)는 "두 스레드의 *첫* 조회"에만 걸리고,
            // 나중에 패배한 쪽이 재조회할 때는 걸리지 않는다(그때는 이미 한쪽이 커밋한 뒤라
            // 다시 기다리게 하면 영원히 안 풀린다).
            CyclicBarrier bothCheckedEmpty = new CyclicBarrier(2);
            FindCallCountingRepository repositoryForA =
                    new FindCallCountingRepository(pushSubscriptionRepository, bothCheckedEmpty);
            FindCallCountingRepository repositoryForB =
                    new FindCallCountingRepository(pushSubscriptionRepository, bothCheckedEmpty);
            PushSubscriptionService serviceForA = new PushSubscriptionService(repositoryForA.proxy(), VAPID_PROPERTIES);
            PushSubscriptionService serviceForB = new PushSubscriptionService(repositoryForB.proxy(), VAPID_PROPERTIES);

            List<Callable<Void>> tasks = List.of(
                    () -> {
                        serviceForA.subscribe(userId, requestA);
                        return null;
                    },
                    () -> {
                        serviceForB.subscribe(userId, requestB);
                        return null;
                    });
            List<ConcurrentRunner.Outcome<Void>> outcomes = ConcurrentRunner.runTogether(tasks, TIMEOUT);

            assertThat(outcomes).as("두 요청 모두 성공해야 한다").allMatch(ConcurrentRunner.Outcome::succeeded);

            int totalFindCalls = repositoryForA.findCallCount.get() + repositoryForB.findCallCount.get();
            assertThat(totalFindCalls)
                    .as("정확히 한쪽만 재조회(복구)했어야 한다 — 최초 조회 2번 + 재조회 1번 = 3번")
                    .isEqualTo(3);

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

    /**
     * 실제 {@link PushSubscriptionRepository}를 감싸는 동적 프록시. {@code
     * findByUserIdAndEndpoint}의 "이 인스턴스에서의 첫 호출"에서만 결과를 돌려주기 전에
     * {@code barrier}에서 기다린다 — 두 스레드가 각자 자기 인스턴스를 쓰므로 "첫 호출"이
     * 스레드마다 정확히 한 번씩만 걸린다. 재조회(두 번째 호출)는 기다리지 않고 바로
     * 진행한다.
     */
    private static final class FindCallCountingRepository implements InvocationHandler {
        private final PushSubscriptionRepository delegate;
        private final CyclicBarrier bothCheckedEmpty;
        private final AtomicInteger findCallCount = new AtomicInteger();

        FindCallCountingRepository(PushSubscriptionRepository delegate, CyclicBarrier bothCheckedEmpty) {
            this.delegate = delegate;
            this.bothCheckedEmpty = bothCheckedEmpty;
        }

        PushSubscriptionRepository proxy() {
            return (PushSubscriptionRepository) Proxy.newProxyInstance(
                    PushSubscriptionRepository.class.getClassLoader(),
                    new Class<?>[]{PushSubscriptionRepository.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxyInstance, Method method, Object[] args) throws Throwable {
            boolean isFirstFindCall = "findByUserIdAndEndpoint".equals(method.getName())
                    && findCallCount.getAndIncrement() == 0;
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException exception) {
                throw exception.getCause();
            }
            if (isFirstFindCall) {
                // 둘 다 "없음"을 확인할 때까지 기다린 뒤에야 이 결과를 돌려준다 — 그래야 둘 다
                // INSERT 쪽으로 진행해서 실제 unique 충돌이 강제로 일어난다.
                bothCheckedEmpty.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            }
            return result;
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
