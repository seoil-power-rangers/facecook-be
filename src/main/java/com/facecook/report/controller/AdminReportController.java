package com.facecook.report.controller;

import com.facecook.common.session.AdminAuthorization;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.report.dto.ReportChatMessageResponse;
import com.facecook.report.dto.ReportResponse;
import com.facecook.report.dto.ResolveReportRequest;
import com.facecook.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자용 신고 API. 모든 메서드가 첫 줄에서 {@code AdminAuthorization.requireAdmin}으로 역할을 확인한다
 * (슈퍼 계정도 여기서 403이다 — 역할이 {@code ADMIN}인지만 본다).
 *
 * <table>
 * <caption>API → 서비스</caption>
 * <tr><td>GET /api/admin/reports</td><td>{@link ReportService#getAll}</td><td>전체 신고, 최신 접수순</td></tr>
 * <tr><td>GET /api/admin/reports/{reportId}</td><td>{@link ReportService#get}</td><td>신고 하나</td></tr>
 * <tr><td>POST /api/admin/reports/{reportId}/resolve</td><td>{@link ReportService#resolve}</td><td>처리 완료(+ 선택적으로 정지)</td></tr>
 * <tr><td>GET /api/admin/reports/{reportId}/chat</td><td>{@link ReportService#getChat}</td><td>신고자·대상 사이 채팅 이력</td></tr>
 * </table>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reports")
public class AdminReportController {
    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<List<ReportResponse>> getAll(@CurrentUser AuthenticatedUser currentUser) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(reportService.getAll());
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ReportResponse> get(
            @PathVariable Long reportId,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(reportService.get(reportId));
    }

    @PostMapping("/{reportId}/resolve")
    public ResponseEntity<ReportResponse> resolve(
            @PathVariable Long reportId,
            @Valid @RequestBody ResolveReportRequest request,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(reportService.resolve(reportId, currentUser.userId(), request));
    }

    @GetMapping("/{reportId}/chat")
    public ResponseEntity<List<ReportChatMessageResponse>> getChat(
            @PathVariable Long reportId,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(reportService.getChat(reportId));
    }
}
