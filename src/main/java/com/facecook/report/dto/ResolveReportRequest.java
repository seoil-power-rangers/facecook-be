package com.facecook.report.dto;

import jakarta.validation.constraints.NotNull;

public record ResolveReportRequest(
        @NotNull(message = "suspend는 필수입니다.") Boolean suspend
) {
}
