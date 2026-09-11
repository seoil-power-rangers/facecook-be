package com.facecook.mission.controller;

import com.facecook.common.session.AdminAuthorization;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.mission.dto.AdminMissionProgressResponse;
import com.facecook.mission.service.MissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/missions")
public class AdminMissionController {
    private final MissionService missionService;

    @GetMapping
    public ResponseEntity<List<AdminMissionProgressResponse>> getAllProgress(
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(missionService.getAllProgress());
    }

    @PostMapping("/{matchId}/complete")
    public ResponseEntity<AdminMissionProgressResponse> completeCurrentStep(
            @PathVariable Long matchId,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(missionService.completeCurrentStep(matchId, currentUser.userId()));
    }
}
