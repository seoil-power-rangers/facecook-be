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
