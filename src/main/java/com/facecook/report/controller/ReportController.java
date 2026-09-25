package com.facecook.report.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.report.dto.CreateReportRequest;
import com.facecook.report.dto.ReportResponse;
import com.facecook.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 참가자용 신고 접수 API. {@code POST /api/reports} → {@link ReportService#create}.
 *
 * <p>역할 검사가 없다 — 로그인만 돼 있으면({@code SessionAuthenticationInterceptor}) 누구나 신고할 수 있다.
 * 신고자는 요청 본문이 아니라 세션({@code @CurrentUser})에서 가져온다.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportResponse> create(
            @Valid @RequestBody CreateReportRequest request,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok(reportService.create(currentUser.userId(), request));
    }
}
