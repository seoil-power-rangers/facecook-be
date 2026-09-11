package com.facecook.report.repository;

import com.facecook.report.entity.Report;
import com.facecook.report.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    long countByStatus(ReportStatus status);

    List<Report> findAllByOrderByCreatedAtDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from Report report where report.id = :reportId")
    Optional<Report> findByIdForUpdate(@Param("reportId") Long reportId);

    @Query(value = """
            select message.message_id as messageId,
                   message.match_id as matchId,
                   message.sender_id as senderId,
                   message.content as content,
                   message.sent_at as sentAt
            from message
            join match_info on match_info.match_id = message.match_id
            where (match_info.user_a_id = :reporterId and match_info.user_b_id = :reportedUserId)
               or (match_info.user_a_id = :reportedUserId and match_info.user_b_id = :reporterId)
            order by message.sent_at asc, message.message_id asc
            """, nativeQuery = true)
    List<ReportChatMessageProjection> findChatMessages(
            @Param("reporterId") Long reporterId,
            @Param("reportedUserId") Long reportedUserId
    );
}
