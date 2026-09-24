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

/**
 * 매칭 API({@code /api/matches}). 매칭을 "만드는" API는 없다 — 맞콕이 되는 순간 {@code CookService#send}가 만든다.
 *
 * <table>
 * <tr><th>API</th><th>서비스</th><th>쓰는 화면(FE)</th></tr>
 * <tr><td>GET /api/matches</td><td>{@link MatchService#getMatches}</td><td>매칭 목록, 배지(5초마다 조회)</td></tr>
 * <tr><td>GET /api/matches/{matchId}</td><td>{@link MatchService#getMatch}</td><td>채팅방 상단 정보</td></tr>
 * <tr><td>PATCH /api/matches/{matchId}/read</td><td>{@link MatchService#markRead}</td><td>채팅방 들어갈 때·나갈 때</td></tr>
 * </table>
 *
 * <p>채팅 이력 조회({@code GET /api/matches/{matchId}/messages})는 경로가 비슷하지만
 * {@code ChatRestController}에 있다.</p>
 */
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
