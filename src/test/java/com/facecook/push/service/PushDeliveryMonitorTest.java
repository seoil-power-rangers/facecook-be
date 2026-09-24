package com.facecook.push.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PushDeliveryMonitorTest {

    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch firstTaskStarted = new CountDownLatch(1);
    private final ThreadPoolTaskExecutor executor = executor();
    private final PushDeliveryMonitor monitor = new PushDeliveryMonitor(executor);

    @AfterEach
    void tearDown() {
        release.countDown();
        executor.shutdown();
    }

    @Test
    void quietWhenNothingWentWrong() {
        PushDeliveryMonitor.Snapshot snapshot = monitor.drain();

        assertThat(snapshot.rejected()).isZero();
        assertThat(snapshot.failed()).isZero();
        assertThat(snapshot.needsAttention()).isFalse();
    }

    @Test
    void reportsCountsOnceAndStartsOverForTheNextInterval() {
        monitor.recordRejected();
        monitor.recordRejected();
        monitor.recordFailed();

        PushDeliveryMonitor.Snapshot first = monitor.drain();
        PushDeliveryMonitor.Snapshot second = monitor.drain();

        assertThat(first.rejected()).isEqualTo(2);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(first.needsAttention()).isTrue();
        assertThat(second.rejected()).isZero();
        assertThat(second.failed()).isZero();
    }

    @Test
    void warnsWhenQueueIsHalfFullEvenWithoutFailures() throws InterruptedException {
        // 스레드 1개가 막혀 있고 대기열(4칸) 중 2칸이 찼다.
        executor.execute(() -> {
            firstTaskStarted.countDown();
            blockUntilReleased();
        });
        assertThat(firstTaskStarted.await(5, TimeUnit.SECONDS)).isTrue();
        executor.execute(this::blockUntilReleased);
        executor.execute(this::blockUntilReleased);

        PushDeliveryMonitor.Snapshot snapshot = monitor.drain();

        assertThat(snapshot.activeThreads()).isEqualTo(1);
        assertThat(snapshot.queued()).isEqualTo(2);
        assertThat(snapshot.queueCapacity()).isEqualTo(4);
        assertThat(snapshot.needsAttention()).isTrue();
    }

    private void blockUntilReleased() {
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.initialize();
        return executor;
    }
}
