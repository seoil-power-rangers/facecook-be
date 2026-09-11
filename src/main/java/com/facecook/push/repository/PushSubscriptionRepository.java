package com.facecook.push.repository;

import com.facecook.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByUserIdAndEndpoint(Long userId, String endpoint);

    List<PushSubscription> findAllByUserId(Long userId);

    void deleteAllByUserId(Long userId);
}
