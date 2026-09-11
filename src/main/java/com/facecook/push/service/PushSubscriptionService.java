package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final VapidProperties vapidProperties;

    public String getVapidPublicKey() {
        String publicKey = vapidProperties.publicKey();
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException("VAPID 공개키가 설정되지 않았습니다.");
        }
        return publicKey;
    }

    @Transactional
    public void subscribe(Long userId, PushSubscriptionRequest request) {
        PushSubscription subscription = pushSubscriptionRepository
                .findByUserIdAndEndpoint(userId, request.endpoint())
                .orElseGet(() -> PushSubscription.create(
                        userId,
                        request.endpoint(),
                        request.keys().p256dh(),
                        request.keys().auth()
                ));
        subscription.updateKeys(request.keys().p256dh(), request.keys().auth());
        pushSubscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(Long userId) {
        pushSubscriptionRepository.deleteAllByUserId(userId);
    }
}
