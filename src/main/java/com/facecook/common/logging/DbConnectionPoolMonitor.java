package com.facecook.common.logging;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DB 연결 풀(Hikari)이 모자라 요청이 연결을 기다렸는지 로그로 파악하게 하는 집계기(facecook-be#127).
 *
 * <p>부하가 몰릴 때 느려진 원인이 서버 CPU·DB CPU인지, 연결 풀을 기다린 것인지 구분하려고 만들었다.
 * RDS의 연결 수 지표는 풀이 연결을 항상 열어 두기 때문에(서버당 기본 10개) 포화 여부를 알려 주지 않는다.</p>
 *
 * <p>동작: {@link #sample}이 1초마다 풀의 "사용 중 연결 수"와 "연결을 기다리는 요청 수"를 읽어 최댓값을 모은다.
 * {@link #report}가 30초마다 그 최댓값과, 같은 30초 동안 연결을 얻는 데 걸린 평균 시간
 * (Micrometer가 Hikari에 붙여 두는 {@code hikaricp.connections.acquire} 타이머의 증가분)을 한 줄로 남긴다.
 * 기다린 요청이 한 번도 없었으면 남기지 않는다({@code PushDeliveryMonitor}와 같은 방식).</p>
 *
 * <p>1초 샘플이라 그 사이의 아주 짧은 대기는 놓칠 수 있다. 평균 대기 시간은 모든 획득을 세므로 놓치지 않는다.</p>
 */
@Slf4j
@Component
public class DbConnectionPoolMonitor {

    static final long SAMPLE_INTERVAL_MS = 1_000;
    static final long REPORT_INTERVAL_MS = 30_000;
    static final String ACQUIRE_TIMER = "hikaricp.connections.acquire";

    private final HikariDataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger maxActive = new AtomicInteger();
    private final AtomicInteger maxWaiting = new AtomicInteger();
    private long lastAcquireCount;
    private double lastAcquireTotalMs;

    public DbConnectionPoolMonitor(HikariDataSource dataSource, MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
    }

    /** 풀의 지금 상태를 읽어 이번 구간의 최댓값에 반영한다. 풀이 아직 만들어지기 전이면 건너뛴다. */
    @Scheduled(fixedRate = SAMPLE_INTERVAL_MS)
    public void sample() {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        if (pool == null) {
            return;
        }
        maxActive.accumulateAndGet(pool.getActiveConnections(), Math::max);
        maxWaiting.accumulateAndGet(pool.getThreadsAwaitingConnection(), Math::max);
    }

    @Scheduled(fixedDelay = REPORT_INTERVAL_MS, initialDelay = REPORT_INTERVAL_MS)
    public void report() {
        Snapshot snapshot = drain();
        if (snapshot.needsAttention()) {
            log.warn(
                    "DB 연결 풀 상태(최근 {}초): 연결 대기 최대 {}건, 사용 중 최대 {}/{}, 연결을 얻는 데 걸린 평균 {}ms(획득 {}번)",
                    REPORT_INTERVAL_MS / 1000,
                    snapshot.maxWaiting(), snapshot.maxActive(), snapshot.maxPoolSize(),
                    String.format("%.1f", snapshot.averageAcquireMs()), snapshot.acquireCount()
            );
        }
    }

    /** 이번 구간의 최댓값과 획득 시간 증가분을 꺼내고, 다음 구간을 위해 0으로 되돌린다. */
    synchronized Snapshot drain() {
        long acquireCount = 0;
        double acquireTotalMs = 0;
        Timer timer = meterRegistry.find(ACQUIRE_TIMER).timer();
        if (timer != null) {
            long count = timer.count();
            double totalMs = timer.totalTime(TimeUnit.MILLISECONDS);
            acquireCount = count - lastAcquireCount;
            acquireTotalMs = totalMs - lastAcquireTotalMs;
            lastAcquireCount = count;
            lastAcquireTotalMs = totalMs;
        }
        return new Snapshot(
                maxWaiting.getAndSet(0),
                maxActive.getAndSet(0),
                dataSource.getMaximumPoolSize(),
                acquireCount,
                acquireCount == 0 ? 0 : acquireTotalMs / acquireCount
        );
    }

    record Snapshot(int maxWaiting, int maxActive, int maxPoolSize, long acquireCount, double averageAcquireMs) {

        boolean needsAttention() {
            return maxWaiting > 0;
        }
    }
}
