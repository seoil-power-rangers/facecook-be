package com.facecook.chat.repository;

import com.facecook.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByClientMessageId(UUID clientMessageId);

    List<Message> findByMatchIdOrderByIdDesc(Long matchId, Pageable pageable);

    List<Message> findByMatchIdAndIdLessThanOrderByIdDesc(Long matchId, Long before, Pageable pageable);

    @Query("""
            select message from Message message
            where message.id in (
                select max(latest.id) from Message latest group by latest.matchId
            )
            """)
    List<Message> findLatestPerMatch();

    /** 한 번도 안 읽은 방(마지막 열람 시각 없음)의 안읽음 개수. */
    long countByMatchIdAndSenderIdNot(Long matchId, Long senderId);

    /** 마지막 열람 시각 이후 상대가 보낸 메시지 개수. */
    long countByMatchIdAndSenderIdNotAndSentAtAfter(Long matchId, Long senderId, LocalDateTime after);
}
