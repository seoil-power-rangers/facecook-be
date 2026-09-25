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

/** 신고 저장소. 메서드 이름 규칙으로 만든 쿼리 둘, 잠금 조회 하나, 채팅 이력 네이티브 쿼리 하나. */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 상태별 개수. 관리자 통계의 {@code pendingReports}({@code AdminStatsService}). */
    long countByStatus(ReportStatus status);

    /** 전체 신고를 접수 시각 최신순으로. 같은 시각이면 id가 큰 것부터라 순서가 흔들리지 않는다. */
    List<Report> findAllByOrderByCreatedAtDescIdDesc();

    /**
     * 신고 행을 {@code SELECT ... FOR UPDATE}로 잠그고 읽는다. 트랜잭션이 끝날 때까지 같은 행을 잠그려는
     * 다른 요청은 기다린다. 호출: {@code ReportService#resolve}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from Report report where report.id = :reportId")
    Optional<Report> findByIdForUpdate(@Param("reportId") Long reportId);

    /**
     * 두 사람이 매칭된 방의 메시지를 오래된 순으로 전부. {@code match_info}에서 두 사람이 A·B 어느 쪽에
     * 저장됐든 찾도록 양쪽 조합을 다 본다. 매칭된 적 없으면 빈 목록. 호출: {@code ReportService#getChat}.
     */
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
