package com.facecook.push.service;

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
