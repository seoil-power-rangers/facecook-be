package com.facecook.chat.redis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 접속 기록(#81)을 실제 Redis로 확인한다. 시각은 {@link MutableClock}으로 움직여서, 만료를 실제로
 * 90초 기다리지 않고 검증한다(판정은 점수=만료 시각과 이 시계로 하므로 Redis의 실제 시간과 무관하다).
 *
 * <p>서버 두 대는 같은 Redis를 쓰는 {@link ChatPresenceService} 인스턴스 두 개로 흉내 낸다 — 인스턴스마다
 * 서버 ID가 다르다. "서버 재시작"은 기존 인스턴스를 버리고 새 인스턴스를 만드는 것이다.</p>
 */
class ChatPresenceServiceRedisIntegrationTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private static final long USER = 7L;

    private MutableClock clock;
    private ChatPresenceService serverA;
    private ChatPresenceService serverB;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        clock = new MutableClock(Instant.parse("2026-09-30T03:00:00Z"));
        serverA = new ChatPresenceService(redis, clock);
        serverB = new ChatPresenceService(redis, clock);
    }

    @Test
    void connectedUserIsOnlineAndDisconnectedUserIsOffline() {
        serverA.connected(USER, "s1");
        assertThat(serverA.isConnected(USER)).isTrue();

        serverA.disconnected(USER, "s1");
        assertThat(serverA.isConnected(USER)).isFalse();
        assertThat(redis.hasKey(ChatPresenceService.KEY_PREFIX + USER))
                .as("마지막 기록이 빠지면 키도 남지 않는다").isFalse();
    }

    @Test
    void userStaysOnlineUntilEveryTabIsClosed() {
        serverA.connected(USER, "tab1");
        serverA.connected(USER, "tab2");

        serverA.disconnected(USER, "tab1");
        assertThat(serverA.isConnected(USER)).isTrue();

        serverA.disconnected(USER, "tab2");
        assertThat(serverA.isConnected(USER)).isFalse();
    }

    @Test
    void lateDisconnectOfOldSessionDoesNotRemoveTheNewReconnection() {
        // 재접속(new)이 먼저 등록된 뒤에 옛 세션(old)의 해제 이벤트가 늦게 도착하는 순서.
        serverA.connected(USER, "old");
        serverA.connected(USER, "new");
        serverA.disconnected(USER, "old");

        assertThat(serverA.isConnected(USER)).isTrue();
    }

    @Test
    void anotherServersLiveSessionSurvivesThisServersRestart() {
        serverA.connected(USER, "on-a");
        serverB.connected(USER, "on-b");

        // A가 재시작한다: 새 인스턴스(새 서버 ID)가 뜨고, 기동 시 정리도 돈다.
        ChatPresenceService restartedA = new ChatPresenceService(redis, clock);
        restartedA.removeLegacyKeys();
        clock.advance(Duration.ofSeconds(60));
        serverB.refreshLeases();
        restartedA.refreshLeases();
        clock.advance(Duration.ofSeconds(60));

        assertThat(restartedA.isConnected(USER))
                .as("B의 세션은 B가 계속 연장하므로 살아 있다").isTrue();
        serverB.disconnected(USER, "on-b");
        assertThat(restartedA.isConnected(USER))
                .as("A의 옛 세션은 아무도 연장하지 않아 만료됐다").isFalse();
    }

    @Test
    void crashedServersSessionsExpireWithoutAnyDisconnectEvent() {
        serverA.connected(USER, "s1");

        // A가 강제 종료돼 해제 이벤트도, 연장도 없다.
        clock.advance(ChatPresenceService.LEASE.minusSeconds(1));
        assertThat(serverB.isConnected(USER)).as("만료 직전까지는 접속 중").isTrue();

        clock.advance(Duration.ofSeconds(2));
        assertThat(serverB.isConnected(USER)).as("연장이 멈추면 만료 시간 뒤 미접속").isFalse();
    }

    @Test
    void refreshingKeepsALiveSessionOnlineBeyondOneLease() {
        serverA.connected(USER, "s1");

        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMillis(ChatPresenceService.REFRESH_INTERVAL_MS));
            serverA.refreshLeases();
        }

        assertThat(serverA.isConnected(USER)).isTrue();
    }

    @Test
    void lostRedisRecordIsRewrittenOnTheNextRefresh() {
        // 등록 쓰기가 실패했거나 기록이 유실된 경우: 로컬 목록에는 세션이 있으니 다음 연장에서 복구된다.
        serverA.connected(USER, "s1");
        redis.delete(ChatPresenceService.KEY_PREFIX + USER);
        assertThat(serverA.isConnected(USER)).isFalse();

        serverA.refreshLeases();

        assertThat(serverA.isConnected(USER)).isTrue();
    }

    @Test
    void disconnectedSessionIsNoLongerRefreshed() {
        serverA.connected(USER, "s1");
        serverA.disconnected(USER, "s1");

        serverA.refreshLeases();

        assertThat(serverA.isConnected(USER)).as("끊긴 연결을 다시 살려내지 않는다").isFalse();
    }

    @Test
    void refreshPrunesExpiredMembersLeftByAnotherServer() {
        serverA.connected(USER, "dead-on-a");
        serverB.connected(USER, "live-on-b");
        clock.advance(ChatPresenceService.LEASE.plusSeconds(1));

        serverB.refreshLeases();

        assertThat(redis.opsForZSet().zCard(ChatPresenceService.KEY_PREFIX + USER))
                .as("만료된 A의 멤버는 정리되고 B의 멤버만 남는다").isEqualTo(1L);
    }

    @Test
    void legacyPermanentKeysAreRemovedButNewKeysAreKept() {
        redis.opsForSet().add("facecook:chat:presence:" + USER, "stale-session-from-old-deploy");
        redis.opsForSet().add("facecook:chat:presence:99", "another-stale-session");
        serverA.connected(USER, "s1");

        serverA.removeLegacyKeys();

        assertThat(redis.hasKey("facecook:chat:presence:" + USER)).isFalse();
        assertThat(redis.hasKey("facecook:chat:presence:99")).isFalse();
        assertThat(serverA.isConnected(USER)).as("새 형식 기록은 그대로").isTrue();
    }

    @Test
    void userWithOnlyALegacyStaleRecordIsNotConsideredOnline() {
        // 배포 전부터 영구히 남아 있던 예전 기록 — 새 구조는 이걸 읽지 않으므로 푸시가 다시 나간다.
        redis.opsForSet().add("facecook:chat:presence:" + USER, "stale-session-from-old-deploy");

        assertThat(serverA.isConnected(USER)).isFalse();
    }

    // ---- 연결 해제와 기록 쓰기가 겹치는 경우(리뷰 지적) ----

    @Test
    void disconnectThatSlipsInWhileARefreshIsWritingDoesNotResurrectTheSession() throws Exception {
        PausingScriptTemplate pausing = new PausingScriptTemplate(connectionFactory);
        ChatPresenceService server = new ChatPresenceService(pausing, clock);
        server.connected(USER, "s1");

        pausing.pauseNextWrite();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> refresh = executor.submit(server::refreshLeases);
            assertThat(pausing.awaitPaused()).as("연장이 쓰기 직전에 멈췄다").isTrue();

            server.disconnected(USER, "s1");   // 연장이 쓰기 전에 연결이 끊긴다
            pausing.resume();                  // 늦게 도착한 연장 쓰기
            refresh.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(server.isConnected(USER)).as("끊긴 세션이 되살아나지 않는다").isFalse();
        assertThat(redis.hasKey(ChatPresenceService.KEY_PREFIX + USER)).isFalse();
    }

    @Test
    void disconnectThatSlipsInWhileConnectIsWritingDoesNotLeaveAStaleRecord() throws Exception {
        PausingScriptTemplate pausing = new PausingScriptTemplate(connectionFactory);
        ChatPresenceService server = new ChatPresenceService(pausing, clock);

        pausing.pauseNextWrite();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> connect = executor.submit(() -> server.connected(USER, "s1"));
            assertThat(pausing.awaitPaused()).as("등록이 쓰기 직전에 멈췄다").isTrue();

            server.disconnected(USER, "s1");
            pausing.resume();
            connect.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(server.isConnected(USER)).isFalse();
        assertThat(redis.hasKey(ChatPresenceService.KEY_PREFIX + USER)).isFalse();
    }

    @Test
    void rapidConnectDisconnectWhileRefreshRunsConcurrentlyEndsOffline() throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        Thread refresher = new Thread(() -> {
            while (running.get()) {
                serverA.refreshLeases();
            }
        });
        refresher.start();
        try {
            for (int i = 0; i < 200; i++) {
                serverA.connected(USER, "rapid-" + i);
                serverA.disconnected(USER, "rapid-" + i);
            }
        } finally {
            running.set(false);
            refresher.join(10_000);
        }

        assertThat(serverA.isConnected(USER)).as("모든 세션이 끊겼으니 미접속").isFalse();
        assertThat(redis.hasKey(ChatPresenceService.KEY_PREFIX + USER)).isFalse();
    }

    // ---- 실제 Redis TTL(리뷰 지적: MutableClock만으로는 키 만료를 확인하지 못한다) ----

    @Test
    void everyWrittenKeyCarriesARealRedisTtl() {
        serverA.connected(USER, "s1");

        Long ttlSeconds = redis.getExpire(ChatPresenceService.KEY_PREFIX + USER);
        assertThat(ttlSeconds).as("TTL 없는 키(-1)가 남으면 안 된다")
                .isBetween(1L, ChatPresenceService.LEASE.toSeconds());
    }

    @Test
    void keyPhysicallyDisappearsFromRedisWhenNothingRefreshesIt() throws InterruptedException {
        ChatPresenceService shortLease = new ChatPresenceService(redis, Clock.systemUTC(), Duration.ofSeconds(1));
        shortLease.connected(USER, "s1");
        assertThat(redis.hasKey(ChatPresenceService.KEY_PREFIX + USER)).isTrue();

        Thread.sleep(1_500);   // 서버가 죽어 아무도 연장하지 않는다

        assertThat(redis.hasKey(ChatPresenceService.KEY_PREFIX + USER))
                .as("점수만 만료된 게 아니라 키 자체가 Redis에서 사라진다").isFalse();
    }

    /** 기록 쓰기(Lua 스크립트) 한 번을 테스트가 풀어줄 때까지 멈춰 세우는 템플릿. */
    private static final class PausingScriptTemplate extends StringRedisTemplate {
        private final AtomicBoolean pauseNext = new AtomicBoolean(false);
        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        PausingScriptTemplate(LettuceConnectionFactory factory) {
            super(factory);
        }

        void pauseNextWrite() {
            pauseNext.set(true);
        }

        boolean awaitPaused() throws InterruptedException {
            return paused.await(10, TimeUnit.SECONDS);
        }

        void resume() {
            resume.countDown();
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            if (pauseNext.compareAndSet(true, false)) {
                paused.countDown();
                try {
                    if (!resume.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("테스트가 쓰기를 풀어주지 않았다");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return super.execute(script, keys, args);
        }
    }

    /** 테스트에서 시간을 앞으로 돌리는 시계. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
