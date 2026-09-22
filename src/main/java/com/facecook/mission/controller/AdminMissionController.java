package com.facecook.mission.controller;

import com.facecook.common.session.AdminAuthorization;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.mission.dto.AdminMissionProgressResponse;
import com.facecook.mission.dto.CompleteMissionRequest;
import com.facecook.mission.service.MissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/missions")
public class AdminMissionController {
    private final MissionService missionService;

    /**
     * {@code includeExcluded=true}로 요청하면 {@code {items, excluded}} 객체를,
     * 아니면(파라미터 없음 포함) 구 FE와 호환되는 배열을 그대로 돌려준다.
     */
    @GetMapping
    public ResponseEntity<?> getAllProgress(
            @RequestParam(name = "includeExcluded", required = false, defaultValue = "false")
            boolean includeExcluded,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        if (includeExcluded) {
            return ResponseEntity.ok(missionService.getAllProgressWithExclusions());
        }
        return ResponseEntity.ok(missionService.getAllProgress());
    }

    @PostMapping("/{matchId}/complete")
    public ResponseEntity<AdminMissionProgressResponse> completeCurrentStep(
            @PathVariable Long matchId,
            @Valid @RequestBody CompleteMissionRequest request,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        AdminAuthorization.requireAdmin(currentUser);
        return ResponseEntity.ok(
                missionService.completeCurrentStep(matchId, currentUser.userId(), request.expectedStep())
        );
    }
}
