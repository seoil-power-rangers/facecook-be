package com.facecook.match.repository;

import com.facecook.match.entity.MatchInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchInfoRepository extends JpaRepository<MatchInfo, Long> {

    @Query("""
            select (count(matchInfo) > 0)
            from MatchInfo matchInfo
            where (matchInfo.userAId = :firstUserId and matchInfo.userBId = :secondUserId)
               or (matchInfo.userAId = :secondUserId and matchInfo.userBId = :firstUserId)
            """)
    boolean existsBetween(
            @Param("firstUserId") Long firstUserId,
            @Param("secondUserId") Long secondUserId
    );

    /**
     * A쪽 참가자의 읽음 시각을 {@code readAt}으로 올린다. 이 컬럼만 바꾸는 조건부 UPDATE이고, 기존 값이 없거나
     * {@code readAt}보다 이전일 때만 갱신한다.
     *
     * <p>엔티티를 읽어 고친 뒤 저장하면 행의 모든 컬럼을 UPDATE하므로, 두 참가자가 동시에 읽을 때 서로 상대의
     * 읽음 시각을 예전 값으로 되돌릴 수 있고, 같은 참가자의 요청이 커밋 순서가 뒤바뀌면 시각이 뒤로 물러난다.
     * 읽음 시각은 안읽음 개수의 기준이라 뒤로 물러나면 이미 읽은 메시지가 다시 안읽음으로 나타난다.</p>
     *
     * <p>전제조건: {@code matchId} 매칭이 존재하고 호출한 쪽이 A쪽 참가자다(호출부가 확인한다). 호출한
     * 트랜잭션이 필요하다.</p>
     *
     * <p>부작용: 행 하나를 UPDATE한다. 영속성 컨텍스트를 비우므로 이 호출 전에 읽은 {@link MatchInfo}는 더 이상
     * 최신이 아니다.</p>
     *
     * @return 갱신한 행 수. 0이면 이미 같거나 더 나중 시각이 기록돼 있어 바꾸지 않았다.
     * @see #markReadAsUserB
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MatchInfo matchInfo
            set matchInfo.userALastReadAt = :readAt
            where matchInfo.id = :matchId
              and (matchInfo.userALastReadAt is null or matchInfo.userALastReadAt < :readAt)
            """)
    int markReadAsUserA(@Param("matchId") Long matchId, @Param("readAt") LocalDateTime readAt);

    /** {@link #markReadAsUserA}와 같은 규칙으로 B쪽 참가자의 읽음 시각만 올린다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MatchInfo matchInfo
            set matchInfo.userBLastReadAt = :readAt
            where matchInfo.id = :matchId
              and (matchInfo.userBLastReadAt is null or matchInfo.userBLastReadAt < :readAt)
            """)
    int markReadAsUserB(@Param("matchId") Long matchId, @Param("readAt") LocalDateTime readAt);

    List<MatchInfo> findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(Long userAId, Long userBId);

    List<MatchInfo> findAllByOrderByMatchedAtDesc();

    /**
     * 매칭마다 최근 메시지를 따로 조회하면 N+1이 난다 — match_id 목록을 한 번에
     * 받아서 매칭별 최신 메시지 하나씩만 골라온다. 단건 조회였던 원래 쿼리의
     * 정렬 기준(sent_at desc, message_id desc)을 그대로 유지해야 한다 — 그냥
     * max(message_id)만 쓰면, sent_at 저장 순서와 message_id 순서가 어긋나는
     * 드문 경우(동시 삽입 등)에 원래 응답과 달라질 수 있다.
     */
    @Query(value = """
            select ranked.matchId as matchId,
                   ranked.senderId as senderId,
                   ranked.content as content,
                   ranked.sentAt as sentAt
            from (
                select m.match_id as matchId,
                       m.sender_id as senderId,
                       m.content as content,
                       m.sent_at as sentAt,
                       row_number() over (
                           partition by m.match_id
                           order by m.sent_at desc, m.message_id desc
                       ) as rn
                from message m
                where m.match_id in (:matchIds)
            ) ranked
            where ranked.rn = 1
            """, nativeQuery = true)
    List<RecentMessageProjection> findRecentMessagesByMatchIds(@Param("matchIds") List<Long> matchIds);
}
