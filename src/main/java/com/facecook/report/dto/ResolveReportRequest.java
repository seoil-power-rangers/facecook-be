package com.facecook.report.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 신고 처리 요청 본문. {@code suspend}가 true면 신고 대상 계정을 정지하고, false면 처리 완료만 기록한다.
 * 원시형 {@code boolean}이면 JSON에서 값을 빠뜨렸을 때 false로 들어오지만, {@code Boolean} + {@code @NotNull}이라
 * null이 되어 400 {@code VALIDATION}이 된다({@code ReportControllerTest#resolveRequiresSuspendDecision}).
 */
public record ResolveReportRequest(
        @NotNull(message = "suspend는 필수입니다.") Boolean suspend
) {
}
