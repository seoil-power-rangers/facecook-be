package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushSubscriptionServiceTest {
    private PushSubscriptionRepository pushSubscriptionRepository;
    private PushSubscriptionService pushSubscriptionService;

    @BeforeEach
    void setUp() {
        pushSubscriptionRepository = mock(PushSubscriptionRepository.class);
        pushSubscriptionService = new PushSubscriptionService(
                pushSubscriptionRepository,
                new VapidProperties("test-public-key", "test-private-key")
        );
    }

    @Test
    void returnsConfiguredVapidPublicKey() {
        assertThat(pushSubscriptionService.getVapidPublicKey()).isEqualTo("test-public-key");
    }

    @Test
    void rejectsMissingVapidPublicKey() {
        PushSubscriptionService serviceWithoutKey = new PushSubscriptionService(
                pushSubscriptionRepository,
                new VapidProperties("", "test-private-key")
        );

        assertThatThrownBy(serviceWithoutKey::getVapidPublicKey)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsSubscriptionForNewEndpoint() {
        PushSubscriptionRequest request = request("new-p256dh", "new-auth");
        when(pushSubscriptionRepository.findByUserIdAndEndpoint(1L, "https://push.example/subscription"))
                .thenReturn(Optional.empty());

        pushSubscriptionService.subscribe(1L, request);

        verify(pushSubscriptionRepository).save(any(PushSubscription.class));
    }

    @Test
    void updatesKeysForExistingEndpoint() {
        PushSubscription subscription = PushSubscription.create(
                1L,
                "https://push.example/subscription",
                "old-p256dh",
                "old-auth"
        );
        when(pushSubscriptionRepository.findByUserIdAndEndpoint(1L, subscription.getEndpoint()))
                .thenReturn(Optional.of(subscription));

        pushSubscriptionService.subscribe(1L, request("new-p256dh", "new-auth"));

        assertThat(subscription.getP256dh()).isEqualTo("new-p256dh");
        assertThat(subscription.getAuth()).isEqualTo("new-auth");
        verify(pushSubscriptionRepository).save(subscription);
    }

    @Test
    void retriesAsUpdateWhenConcurrentInsertLosesTheUniqueConstraintRace() {
        // 첫 조회 때는 아직 없었다가(둘 다 "없음"으로 보고 INSERT를 시도하는 경합
        // 상황을 흉내), save()가 나중에 커밋되는 쪽이라 unique 제약에 막힌다. 재조회하면
        // 먼저 이긴 쪽이 이미 커밋해 둔 행이 보인다.
        PushSubscription winnerRow = PushSubscription.create(
                1L, "https://push.example/subscription", "other-p256dh", "other-auth");
        when(pushSubscriptionRepository.findByUserIdAndEndpoint(1L, "https://push.example/subscription"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerRow));
        when(pushSubscriptionRepository.save(any(PushSubscription.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        pushSubscriptionService.subscribe(1L, request("new-p256dh", "new-auth"));

        assertThat(winnerRow.getP256dh()).isEqualTo("new-p256dh");
        assertThat(winnerRow.getAuth()).isEqualTo("new-auth");
        verify(pushSubscriptionRepository, times(2)).findByUserIdAndEndpoint(1L, "https://push.example/subscription");
        verify(pushSubscriptionRepository, times(2)).save(any(PushSubscription.class));
    }

    @Test
    void rethrowsOriginalExceptionWhenRetryLookupStillFindsNothing() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("duplicate key");
        when(pushSubscriptionRepository.findByUserIdAndEndpoint(1L, "https://push.example/subscription"))
                .thenReturn(Optional.empty());
        when(pushSubscriptionRepository.save(any(PushSubscription.class))).thenThrow(original);

        assertThatThrownBy(() -> pushSubscriptionService.subscribe(1L, request("new-p256dh", "new-auth")))
                .isSameAs(original);
    }

    @Test
    void removesAllSubscriptionsForUser() {
        pushSubscriptionService.unsubscribe(1L);

        verify(pushSubscriptionRepository).deleteAllByUserId(1L);
        verify(pushSubscriptionRepository, never()).deleteAll();
    }

    private static PushSubscriptionRequest request(String p256dh, String auth) {
        return new PushSubscriptionRequest(
                "https://push.example/subscription",
                new PushSubscriptionRequest.Keys(p256dh, auth)
        );
    }
}
