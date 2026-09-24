package com.facecook.push.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 푸시 유실을 로그로 파악할 수 있게 하는 집계기(facecook-be#113). 거절·실패는
 * 건마다 남기면 푸시가 몰릴 때 로그가 넘치고, 안 남기면 얼마나 빠졌는지 모른다.
 * 그래서 횟수만 세 두고 {@link #REPORT_INTERVAL_MS}마다 한 줄로 남긴다. 아무 일도
 * 없으면(거절·실패 0, 대기열 여유) 남기지 않는다.
 */
@Slf4j
@Component
public class PushDeliveryMonitor {

    static final long REPORT_INTERVAL_MS = 30_000;
    /** 대기열이 이 비율 이상 차 있으면 실패가 없어도 경고한다 — 곧 거절이 시작된다는 신호다. */
    static final double QUEUE_WARNING_RATIO = 0.5;

    private final ThreadPoolTaskExecutor pushExecutor;
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public PushDeliveryMonitor(@Qualifier("pushExecutor") ThreadPoolTaskExecutor pushExecutor) {
        this.pushExecutor = pushExecutor;
    }

    /** 실행기 대기열이 가득 차서 발송 자체를 못 맡긴 경우. */
    public void recordRejected() {
        rejected.incrementAndGet();
    }

    /** 웹 푸시 서버 호출이 실패했거나(제한 시간 초과 포함) 서버가 발송을 거부한 경우. 만료 구독 삭제는 세지 않는다. */
    public void recordFailed() {
        failed.incrementAndGet();
    }

    @Scheduled(fixedDelay = REPORT_INTERVAL_MS)
    public void report() {
        Snapshot snapshot = drain();
        if (snapshot.needsAttention()) {
            log.warn(
                    "푸시 발송 상태(최근 {}초): 거절 {}건, 실패 {}건, 실행 중 {}/{}, 대기열 {}/{}",
                    REPORT_INTERVAL_MS / 1000,
                    snapshot.rejected(), snapshot.failed(),
                    snapshot.activeThreads(), snapshot.maxThreads(),
                    snapshot.queued(), snapshot.queueCapacity()
            );
        }
    }

    /** 지금까지 센 횟수를 꺼내고 0으로 되돌린 뒤, 실행기의 현재 상태와 함께 돌려준다. */
    Snapshot drain() {
        return new Snapshot(
                rejected.getAndSet(0),
                failed.getAndSet(0),
                pushExecutor.getActiveCount(),
                pushExecutor.getMaxPoolSize(),
                pushExecutor.getQueueSize(),
                pushExecutor.getQueueCapacity()
        );
    }

    record Snapshot(long rejected, long failed, int activeThreads, int maxThreads, int queued, int queueCapacity) {

        boolean needsAttention() {
            return rejected > 0 || failed > 0 || queued >= queueCapacity * QUEUE_WARNING_RATIO;
        }
    }
}
