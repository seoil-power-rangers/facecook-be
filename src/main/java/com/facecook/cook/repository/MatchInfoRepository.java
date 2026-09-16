package com.facecook.cook.repository;

import com.facecook.cook.entity.MatchInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    @Query(value = """
            select message.sender_id as senderId,
                   message.content as content,
                   message.sent_at as sentAt
            from message
            where message.match_id = :matchId
            order by message.sent_at desc, message.message_id desc
            limit 1
            """, nativeQuery = true)
    Optional<RecentMessageProjection> findRecentMessage(@Param("matchId") Long matchId);
}
