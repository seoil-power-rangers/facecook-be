package com.facecook.report.dto;

import com.facecook.report.entity.Report;

import java.time.LocalDateTime;

public record ReportResponse(
        Long reportId,
        Long reporterId,
        Long reportedUserId,
        String reason,
        String detail,
        String status,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReporterId(),
                report.getReportedUserId(),
                report.getReason(),
                report.getDetail(),
                report.getStatus().name().toLowerCase(),
                report.getReviewedBy(),
                report.getReviewedAt(),
                report.getCreatedAt()
        );
    }
}
