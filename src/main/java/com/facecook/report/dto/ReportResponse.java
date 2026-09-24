package com.facecook.report.dto;

import com.facecook.report.entity.Report;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 신고 한 건의 응답. {@code status}는 {@code "pending"}/{@code "reviewed"} 소문자 문자열이고,
 * {@code reviewedBy}·{@code reviewedAt}은 처리 전에는 null이다.
 *
 * <p>{@code toLowerCase(Locale.ROOT)}: 서버의 기본 로케일이 터키어 등이면 대문자 {@code I}가 다른 글자로 바뀐다.
 * 로케일과 무관하게 같은 결과를 내려고 {@code Locale.ROOT}를 준다({@code ReportResponseTest}가 확인).</p>
 */
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
                report.getStatus().name().toLowerCase(Locale.ROOT),
                report.getReviewedBy(),
                report.getReviewedAt(),
                report.getCreatedAt()
        );
    }
}
