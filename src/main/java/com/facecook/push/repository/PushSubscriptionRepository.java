package com.facecook.push.repository;

import com.facecook.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 푸시 구독 조회·저장. 등록·해지({@code PushSubscriptionService}), 발송 대상 조회·만료 삭제({@code PushDeliveryService}). */
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByUserIdAndEndpoint(Long userId, String endpoint);

    List<PushSubscription> findAllByUserId(Long userId);

    void deleteAllByUserId(Long userId);
}
