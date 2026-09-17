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
     * 받아서 매칭별 최신 메시지 하나씩만 조인으로 골라온다.
     */
    @Query(value = """
            select m.match_id as matchId,
                   m.sender_id as senderId,
                   m.content as content,
                   m.sent_at as sentAt
            from message m
            inner join (
                select match_id, max(message_id) as message_id
                from message
                where match_id in (:matchIds)
                group by match_id
            ) latest on latest.match_id = m.match_id and latest.message_id = m.message_id
            """, nativeQuery = true)
    List<RecentMessageProjection> findRecentMessagesByMatchIds(@Param("matchIds") List<Long> matchIds);
}
