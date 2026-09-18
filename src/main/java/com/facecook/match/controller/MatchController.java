package com.facecook.match.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.match.dto.MatchResponse;
import com.facecook.match.service.MatchService;
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

    private final MatchService matchService;

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getMatches(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(matchService.getMatches(currentUser.userId()));
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<MatchResponse> getMatch(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long matchId
    ) {
        return ResponseEntity.ok(matchService.getMatch(currentUser.userId(), matchId));
    }

    @PatchMapping("/{matchId}/read")
    public ResponseEntity<Void> markRead(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long matchId
    ) {
        matchService.markRead(currentUser.userId(), matchId);
        return ResponseEntity.noContent().build();
    }
}
