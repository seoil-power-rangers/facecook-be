package com.facecook.mission.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.mission.dto.MissionProgressResponse;
import com.facecook.mission.service.MissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/matches/{matchId}/mission")
public class MissionController {
    private final MissionService missionService;

    @GetMapping
    public ResponseEntity<MissionProgressResponse> getProgress(
            @PathVariable Long matchId,
            @CurrentUser AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok(missionService.getProgress(matchId, currentUser.userId()));
    }
}
