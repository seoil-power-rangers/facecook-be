package com.facecook.common.logging;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbConnectionPoolMonitorTest {

    private final HikariDataSource dataSource = mock(HikariDataSource.class);
    private final HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DbConnectionPoolMonitor monitor = new DbConnectionPoolMonitor(dataSource, registry);

    @BeforeEach
    void setUp() {
        when(dataSource.getHikariPoolMXBean()).thenReturn(pool);
        when(dataSource.getMaximumPoolSize()).thenReturn(10);
    }

    @Test
    void quietWhenNoRequestWaitedForAConnection() {
        sample(4, 0);
        sample(7, 0);

        DbConnectionPoolMonitor.Snapshot snapshot = monitor.drain();

        assertThat(snapshot.maxActive()).isEqualTo(7);
        assertThat(snapshot.maxWaiting()).isZero();
        assertThat(snapshot.needsAttention()).isFalse();
    }

    @Test
    void reportsPeakWaitingAndStartsOverForTheNextInterval() {
        sample(10, 3);
        sample(10, 12);
        sample(6, 0);

        DbConnectionPoolMonitor.Snapshot first = monitor.drain();
        DbConnectionPoolMonitor.Snapshot second = monitor.drain();

        assertThat(first.maxWaiting()).isEqualTo(12);
        assertThat(first.maxActive()).isEqualTo(10);
        assertThat(first.maxPoolSize()).isEqualTo(10);
        assertThat(first.needsAttention()).isTrue();
        assertThat(second.maxWaiting()).isZero();
        assertThat(second.needsAttention()).isFalse();
    }

    @Test
    void averagesOnlyTheAcquiresRecordedDuringTheInterval() {
        Timer acquire = registry.timer(DbConnectionPoolMonitor.ACQUIRE_TIMER);
        acquire.record(Duration.ofMillis(100));
        monitor.drain(); // 앞 구간: 100ms 1번

        acquire.record(Duration.ofMillis(10));
        acquire.record(Duration.ofMillis(30));
        DbConnectionPoolMonitor.Snapshot snapshot = monitor.drain();

        assertThat(snapshot.acquireCount()).isEqualTo(2);
        assertThat(snapshot.averageAcquireMs()).isEqualTo(20.0);
    }

    @Test
    void skipsSamplingBeforeThePoolStarts() {
        when(dataSource.getHikariPoolMXBean()).thenReturn(null);

        monitor.sample();
        DbConnectionPoolMonitor.Snapshot snapshot = monitor.drain();

        assertThat(snapshot.maxActive()).isZero();
        assertThat(snapshot.acquireCount()).isZero();
        assertThat(snapshot.averageAcquireMs()).isZero();
    }

    private void sample(int active, int waiting) {
        when(pool.getActiveConnections()).thenReturn(active);
        when(pool.getThreadsAwaitingConnection()).thenReturn(waiting);
        monitor.sample();
    }
}
