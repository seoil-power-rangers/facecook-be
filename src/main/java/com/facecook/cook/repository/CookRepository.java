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

    /** 특정 유저에 한정하지 않는, 그 시간대에 전체 참가자가 보낸 콕 수(행사 전체 총량 제한용). */
    long countBySentAtGreaterThanEqualAndSentAtLessThan(
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    );
}
