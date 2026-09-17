package com.facecook.cook.repository;

import com.facecook.cook.entity.MatchInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
