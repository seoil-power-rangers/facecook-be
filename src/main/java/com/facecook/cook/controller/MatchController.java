package com.facecook.cook.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.cook.dto.MatchResponse;
import com.facecook.cook.service.CookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/matches")
public class MatchController {

    private final CookService cookService;

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getMatches(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(cookService.getMatches(currentUser.userId()));
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<MatchResponse> getMatch(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long matchId
    ) {
        return ResponseEntity.ok(cookService.getMatch(currentUser.userId(), matchId));
    }

    @PatchMapping("/{matchId}/read")
    public ResponseEntity<Void> markRead(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long matchId
    ) {
        cookService.markRead(currentUser.userId(), matchId);
        return ResponseEntity.noContent().build();
    }
}
