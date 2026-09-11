package com.facecook.push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.push.dto.PushNotificationPayload;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeliveryService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushGateway webPushGateway;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendToUser(Long userId, PushNotificationPayload payload) {
        String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.error("푸시 페이로드 직렬화에 실패했습니다. userId={}, type={}", userId, payload.type(), exception);
            return;
        }

        for (PushSubscription subscription : pushSubscriptionRepository.findAllByUserId(userId)) {
            send(subscription, serializedPayload);
        }
    }

    private void send(PushSubscription subscription, String payload) {
        try {
            PushDeliveryResult result = webPushGateway.send(subscription, payload);
            if (result.successful()) {
                return;
            }
            if (result.subscriptionExpired()) {
                pushSubscriptionRepository.delete(subscription);
                log.info(
                        "만료된 푸시 구독을 삭제했습니다. userId={}, subscriptionId={}, status={}",
                        subscription.getUserId(), subscription.getId(), result.statusCode()
                );
                return;
            }
            log.warn(
                    "웹 푸시 서버가 발송을 거부했습니다. userId={}, subscriptionId={}, status={}, reason={}",
                    subscription.getUserId(), subscription.getId(), result.statusCode(), result.reason()
            );
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn(
                    "웹 푸시 발송에 실패했습니다. userId={}, subscriptionId={}",
                    subscription.getUserId(), subscription.getId(), exception
            );
        }
    }
}
