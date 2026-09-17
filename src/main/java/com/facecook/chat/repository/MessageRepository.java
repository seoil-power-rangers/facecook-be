package com.facecook.chat.repository;

import com.facecook.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 매칭마다 안읽음 개수를 따로 세면 N+1이 난다 — match_id 목록을 한 번에
     * 받아서 상대가 보낸 메시지의 시각만 가져오고, 매칭별 lastReadAt 기준
     * 필터링은 호출부에서 메모리에서 처리한다(매칭마다 lastReadAt이 달라서
     * 쿼리 하나로 필터링 조건까지 표현하기 어렵다).
     */
    @Query("""
            select message.matchId as matchId, message.sentAt as sentAt
            from Message message
            where message.matchId in :matchIds and message.senderId <> :currentUserId
            """)
    List<UnreadMessageProjection> findSentAtForUnreadCount(
            @Param("matchIds") List<Long> matchIds,
            @Param("currentUserId") Long currentUserId
    );
}
