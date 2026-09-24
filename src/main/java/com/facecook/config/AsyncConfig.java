package com.facecook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

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
