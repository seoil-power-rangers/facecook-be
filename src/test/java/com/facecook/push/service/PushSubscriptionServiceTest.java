package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
