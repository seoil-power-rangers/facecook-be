package com.facecook.push.service;

import com.facecook.config.AsyncConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.push.dto.PushNotificationPayload;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PushDeliveryService#sendToUser}가 실제로 요청 스레드를 막지 않고
 * 전용 실행기(pushExecutor)에서 도는지 확인한다(facecook-be#82). 직접
 * 인스턴스화하는 {@link PushDeliveryServiceTest}는 {@code @Async} 프록시를
 * 거치지 않아 같은 스레드에서 동기로 실행되므로 이 성질을 못 잡는다 —
 * Spring 컨텍스트를 띄워 실제 프록시로 호출해야 한다.
 */
class PushDeliveryServiceAsyncIntegrationTest {

    @Configuration
    @Import(AsyncConfig.class)
    static class TestConfig {

        @Bean
        PushSubscriptionRepository pushSubscriptionRepository() {
            return mock(PushSubscriptionRepository.class);
        }

        @Bean
        WebPushGateway webPushGateway() {
            return mock(WebPushGateway.class);
        }

        @Bean
        PushDeliveryService pushDeliveryService(
                PushSubscriptionRepository repository, WebPushGateway gateway) {
            return new PushDeliveryService(repository, gateway, new ObjectMapper());
        }
    }

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void callerThreadReturnsBeforeSlowGatewayCallFinishesAndRunsOnDedicatedExecutor() throws Exception {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        PushSubscriptionRepository repository = context.getBean(PushSubscriptionRepository.class);
        WebPushGateway gateway = context.getBean(WebPushGateway.class);
        PushDeliveryService pushDeliveryService = context.getBean(PushDeliveryService.class);

        PushSubscription subscription =
                PushSubscription.create(9L, "https://push.example/x", "p256dh", "auth");
        when(repository.findAllByUserId(9L)).thenReturn(List.of(subscription));

        CountDownLatch gatewayEntered = new CountDownLatch(1);
        CountDownLatch releaseGateway = new CountDownLatch(1);
        AtomicReference<String> executingThreadName = new AtomicReference<>();
        when(gateway.send(eq(subscription), anyString())).thenAnswer(invocation -> {
            executingThreadName.set(Thread.currentThread().getName());
            gatewayEntered.countDown();
            releaseGateway.await(5, TimeUnit.SECONDS);
            return new PushDeliveryResult(201, "Created");
        });

        String callerThreadName = Thread.currentThread().getName();
        long start = System.nanoTime();
        pushDeliveryService.sendToUser(9L, new PushNotificationPayload("COOK_RECEIVED", "t", "b", "/main"));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        try {
            // 게이트웨이가 5초 동안 안 풀리는데도 호출이 거의 즉시 반환돼야
            // 한다 — 그래야 콕·채팅 전송 응답이 푸시 완료를 안 기다린다.
            assertThat(elapsedMs).isLessThan(200);
            assertThat(gatewayEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(executingThreadName.get()).isNotEqualTo(callerThreadName);
            assertThat(executingThreadName.get()).startsWith("push-");
        } finally {
            releaseGateway.countDown();
        }
    }

    @Test
    void queueSaturationIsRejectedRatherThanBlockingTheCaller() throws Exception {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        PushSubscriptionRepository repository = context.getBean(PushSubscriptionRepository.class);
        WebPushGateway gateway = context.getBean(WebPushGateway.class);
        PushDeliveryService pushDeliveryService = context.getBean(PushDeliveryService.class);

        PushSubscription subscription =
                PushSubscription.create(9L, "https://push.example/x", "p256dh", "auth");
        when(repository.findAllByUserId(9L)).thenReturn(List.of(subscription));

        CountDownLatch releaseAll = new CountDownLatch(1);
        when(gateway.send(eq(subscription), anyString())).thenAnswer(invocation -> {
            releaseAll.await(5, TimeUnit.SECONDS);
            return new PushDeliveryResult(201, "Created");
        });

        // pushExecutor: core=4, max=8, queue=200 — 워커·큐를 전부 채우고
        // 하나 더 보내면 그 마지막 호출은 거절돼야 한다(AbortPolicy).
        // 이 호출 자체가 발송 로직 안에서 예외를 삼키지 않으므로, 거절되면
        // RejectedExecutionException이 호출부까지 그대로 올라온다 —
        // ParticipantPushNotificationService.sendIfOffline이 흡수하는 지점.
        int capacity = 8 + 200;
        for (int i = 0; i < capacity; i++) {
            pushDeliveryService.sendToUser(9L, new PushNotificationPayload("COOK_RECEIVED", "t", "b", "/main"));
        }

        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> pushDeliveryService.sendToUser(9L, new PushNotificationPayload("COOK_RECEIVED", "t", "b", "/main"))
            ).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
        } finally {
            releaseAll.countDown();
        }
    }
}
