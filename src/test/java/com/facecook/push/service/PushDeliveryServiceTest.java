package com.facecook.push.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.push.dto.PushNotificationPayload;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushDeliveryServiceTest {

    private PushSubscriptionRepository pushSubscriptionRepository;
    private WebPushGateway webPushGateway;
    private PushDeliveryService pushDeliveryService;
    private PushSubscription subscription;

    @BeforeEach
    void setUp() {
        pushSubscriptionRepository = mock(PushSubscriptionRepository.class);
        webPushGateway = mock(WebPushGateway.class);
        pushDeliveryService = new PushDeliveryService(
                pushSubscriptionRepository,
                webPushGateway,
                new ObjectMapper()
        );
        subscription = PushSubscription.create(
                2L,
                "https://push.example/subscription",
                "p256dh",
                "auth"
        );
        when(pushSubscriptionRepository.findAllByUserId(2L)).thenReturn(List.of(subscription));
    }

    @Test
    void deletesExpiredSubscriptionAfterGoneResponse() throws Exception {
        when(webPushGateway.send(eq(subscription), anyString()))
                .thenReturn(new PushDeliveryResult(410, "Gone"));

        pushDeliveryService.sendToUser(2L, payload());

        verify(pushSubscriptionRepository).delete(subscription);
    }

    @Test
    void keepsSubscriptionAfterSuccessfulDelivery() throws Exception {
        when(webPushGateway.send(eq(subscription), anyString()))
                .thenReturn(new PushDeliveryResult(201, "Created"));

        pushDeliveryService.sendToUser(2L, payload());

        verify(pushSubscriptionRepository, never()).delete(subscription);
    }

    @Test
    void absorbsTransportFailureWithoutDeletingPotentiallyHealthySubscription() throws Exception {
        when(webPushGateway.send(eq(subscription), anyString()))
                .thenThrow(new IOException("network unavailable"));

        assertThatCode(() -> pushDeliveryService.sendToUser(2L, payload())).doesNotThrowAnyException();

        verify(pushSubscriptionRepository, never()).delete(subscription);
    }

    private PushNotificationPayload payload() {
        return new PushNotificationPayload("COOK_RECEIVED", "title", "body", "/main");
    }
}
