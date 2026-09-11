package com.facecook.cook.repository;

import com.facecook.cook.entity.Cook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CookRepository extends JpaRepository<Cook, Long> {

    boolean existsBySenderIdAndReceiverId(Long senderId, Long receiverId);

    Optional<Cook> findBySenderIdAndReceiverId(Long senderId, Long receiverId);

    List<Cook> findAllBySenderIdOrReceiverIdOrderBySentAtDesc(Long senderId, Long receiverId);

    long countBySenderId(Long senderId);

    long countBySenderIdAndSentAtGreaterThanEqualAndSentAtLessThan(
            Long senderId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );
}
