package com.facecook.cook.repository;

import com.facecook.cook.entity.Cook;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CookRepository extends JpaRepository<Cook, Long> {

    /**
     * 콕의 보낸 사람·받은 사람 ID만 읽는다(엔티티를 로드하지 않는다). 콕의 두 사용자 ID는 바뀌지 않으므로
     * 잠금 전에 읽어도 안전하다.
     */
    @Query("select cook.senderId as senderId, cook.receiverId as receiverId from Cook cook where cook.id = :cookId")
    Optional<CookParticipants> findParticipantsById(@Param("cookId") Long cookId);

    /**
     * 콕 행에 배타 잠금을 걸고 읽는다. 잠금 조회는 다른 트랜잭션이 이미 커밋한 최신 상태를 읽으므로,
     * 상태를 판정하는 취소·거절은 사용자 행을 잠근 뒤 이 메서드로 콕을 처음 읽어야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cook from Cook cook where cook.id = :cookId")
    Optional<Cook> findByIdForUpdate(@Param("cookId") Long cookId);

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
