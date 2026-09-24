package com.facecook.chat.repository;

import com.facecook.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code message} 조회·저장. 호출부: 전송·재전송 확인({@code ChatService}), 이력 페이지({@code ChatMessagePageReader}),
 * 안읽음 개수({@code MatchService}), 방별 마지막 메시지({@code SuperAccountService}).
 *
 * <p>{@code Pageable}을 받는 메서드는 {@code LIMIT}을 붙인다({@code PageRequest.of(0, limit)} — 첫 페이지 limit개).
 * 이력은 "몇 번째 페이지"가 아니라 {@code IdLessThan}(이 id보다 작은 것)으로 넘긴다.</p>
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findByClientMessageId(UUID clientMessageId);

    List<Message> findByMatchIdOrderByIdDesc(Long matchId, Pageable pageable);

    List<Message> findByMatchIdAndIdLessThanOrderByIdDesc(Long matchId, Long before, Pageable pageable);

    /** 방마다 가장 큰 id의 메시지 하나씩. 슈퍼 계정의 전체 채팅방 목록에서 쓴다({@code SuperAccountService}). */
    @Query("""
            select message from Message message
            where message.id in (
                select max(latest.id) from Message latest group by latest.matchId
            )
            """)
    List<Message> findLatestPerMatch();

    /**
     * 매칭마다 안읽음 개수를 따로 세면 N+1이 난다 — match_id 목록을 한 번에
     * 받아서 매칭별 안읽음 개수를 DB에서 직접 집계한다. 메시지 원본을
     * 애플리케이션으로 가져와 세면 매칭 하나에 메시지가 아주 많이 쌓였을 때
     * 불필요하게 많은 행을 전송하게 된다 — match_info의 user_a_last_read_at/
     * user_b_last_read_at을 그대로 조인해서 COUNT까지 DB에서 끝낸다.
     */
    @Query(value = """
            select mi.match_id as matchId, count(*) as unreadCount
            from match_info mi
            inner join message m on m.match_id = mi.match_id
            where mi.match_id in (:matchIds)
              and m.sender_id <> :currentUserId
              and (
                    (mi.user_a_id = :currentUserId
                        and (mi.user_a_last_read_at is null or m.sent_at > mi.user_a_last_read_at))
                 or (mi.user_b_id = :currentUserId
                        and (mi.user_b_last_read_at is null or m.sent_at > mi.user_b_last_read_at))
              )
            group by mi.match_id
            """, nativeQuery = true)
    List<UnreadCountProjection> findUnreadCountsByMatchIds(
            @Param("matchIds") List<Long> matchIds,
            @Param("currentUserId") Long currentUserId
    );
}
