package com.facecook.admin.controller;

import com.facecook.admin.dto.AdminStatsResponse;
import com.facecook.admin.service.AdminStatsService;
import com.facecook.common.session.AdminAuthorization;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 대시보드 통계. {@code GET /api/admin/stats} → {@link AdminStatsService#getStats}.
 * 첫 줄에서 {@code AdminAuthorization.requireAdmin}으로 역할을 확인한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stats")
public class AdminStatsController {
    private final AdminStatsService adminStatsService;

    @GetMapping
    public ResponseEntity<AdminStatsResponse> getStats(@CurrentUser AuthenticatedUser currentUser) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(adminStatsService.getStats());
    }
}
