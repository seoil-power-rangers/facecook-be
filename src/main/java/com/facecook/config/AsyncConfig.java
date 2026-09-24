package com.facecook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * {@code @Async} 메서드를 요청 스레드가 아닌 별도 스레드 풀에서 실행하게 켜고,
 * 용도별 스레드 풀(실행기)을 만든다.
 *
 * <p>{@code @Async("이름")}이 붙은 메서드를 다른 빈에서 호출하면, 스프링이 호출을
 * 가로채 해당 이름의 실행기에 작업을 맡기고 바로 반환한다. 호출한 쪽은 결과를
 * 기다리지 않는다. 같은 클래스 안에서 {@code this.method()}로 부르면 가로채지
 * 못해 동기로 실행되니 주의한다.</p>
 *
 * <p>실행기를 용도별로 나눈 이유: 한 작업(예: 느린 푸시 서버)이 밀려도 다른
 * 작업(미션 진행 발행)이 같은 스레드를 기다리지 않게 하기 위해서다.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 미션 진행 상황을 커밋 후 Redis로 발행하는 작업
     * ({@code MissionProgressCommittedListener}) 전용 실행기.
     */
    @Bean(name = "missionEventExecutor")
    Executor missionEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mission-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    /**
     * 웹 푸시 발송(외부 HTTP 왕복이 미션 이벤트 발행보다 오래 걸릴 수 있다) 전용
     * 실행기. missionEventExecutor와 분리해 둬서, 푸시가 밀려도 미션 진행상황
     * Redis 발행 같은 다른 커밋 후 작업이 못 도는 일이 없게 한다(facecook-be#82).
     * 큐가 가득 차면(포화) 기본 정책(AbortPolicy)대로 거절하고, 호출부
     * (ParticipantPushNotificationService.sendIfOffline)가 그 예외를 흡수해
     * 거절 횟수만 센다({@link com.facecook.push.service.PushDeliveryMonitor}) —
     * 푸시는 best-effort라 유실을 감수하고 요청 스레드를 절대 막지 않는 쪽을
     * 택했다.
     */
    @Bean(name = "pushExecutor")
    ThreadPoolTaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("push-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
