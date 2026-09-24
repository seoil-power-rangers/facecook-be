package com.facecook.report.entity;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 신고 한 건({@code report} 테이블). 상태는 {@code PENDING} → {@code REVIEWED} 한 방향으로만 바뀐다.
 *
 * <p>신고자·대상·처리자는 {@code User} 엔티티 연관 없이 ID({@code Long})로만 들고 있다. 세 컬럼 모두 DB에서
 * {@code users}를 가리키는 외래 키다(V1).</p>
 */
@Getter
@Entity
@Table(name = "report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private Long reportedUserId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 1000)
    private String detail;

    @Convert(converter = ReportStatusConverter.class)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private Report(Long reporterId, Long reportedUserId, String reason, String detail, LocalDateTime createdAt) {
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
        this.createdAt = createdAt;
    }

    /** 새 신고. 상태는 항상 {@code PENDING}으로 시작한다. */
    public static Report create(
            Long reporterId,
            Long reportedUserId,
            String reason,
            String detail,
            LocalDateTime createdAt
    ) {
        return new Report(reporterId, reportedUserId, reason, detail, createdAt);
    }

    /**
     * 처리 완료로 바꾸고 처리자·처리 시각을 남긴다. 이미 {@code REVIEWED}면 {@code REPORT_ALREADY_REVIEWED}(409).
     * 두 관리자가 동시에 처리하는 경우는 {@code ReportRepository#findByIdForUpdate}의 행 잠금으로 순서가 정해지고,
     * 뒤에 온 쪽이 이 검사에 걸린다.
     */
    public void review(Long adminId, LocalDateTime reviewedAt) {
        if (status == ReportStatus.REVIEWED) {
            throw new ApiException(ErrorCode.REPORT_ALREADY_REVIEWED);
        }
        this.status = ReportStatus.REVIEWED;
        this.reviewedBy = adminId;
        this.reviewedAt = reviewedAt;
    }
}
