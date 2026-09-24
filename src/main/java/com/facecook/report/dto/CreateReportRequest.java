package com.facecook.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 접수 요청 본문. 검증 실패는 컨트롤러의 {@code @Valid}에서 400 {@code VALIDATION}이 된다.
 * 글자 수 제한은 {@code report} 테이블 컬럼 길이(reason 500, detail 1000)와 같다. {@code detail}은 비워도 된다.
 */
public record CreateReportRequest(
        @NotNull(message = "reportedUserId는 필수입니다.") Long reportedUserId,
        @NotBlank(message = "reason은 필수입니다.")
        @Size(max = 500, message = "reason은 500자 이하여야 합니다.") String reason,
        @Size(max = 1000, message = "detail은 1000자 이하여야 합니다.") String detail
) {
}
