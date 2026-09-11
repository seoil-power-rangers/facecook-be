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
