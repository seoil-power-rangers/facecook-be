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

    public static Report create(
            Long reporterId,
            Long reportedUserId,
            String reason,
            String detail,
            LocalDateTime createdAt
    ) {
        return new Report(reporterId, reportedUserId, reason, detail, createdAt);
    }

    public void review(Long adminId, LocalDateTime reviewedAt) {
        if (status == ReportStatus.REVIEWED) {
            throw new ApiException(ErrorCode.REPORT_ALREADY_REVIEWED);
        }
        this.status = ReportStatus.REVIEWED;
        this.reviewedBy = adminId;
        this.reviewedAt = reviewedAt;
    }
}
