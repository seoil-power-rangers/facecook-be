package com.facecook.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull(message = "reportedUserId는 필수입니다.") Long reportedUserId,
        @NotBlank(message = "reason은 필수입니다.")
        @Size(max = 500, message = "reason은 500자 이하여야 합니다.") String reason,
        @Size(max = 1000, message = "detail은 1000자 이하여야 합니다.") String detail
) {
}
