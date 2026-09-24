package com.facecook.chat.controller;

import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.service.ChatService;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 채팅 이력 조회 REST API. 전송은 WebSocket({@code ChatMessageController})이고, 이력은 REST다 — 화면에 처음
 * 들어올 때, 위로 스크롤해 이전 메시지를 볼 때, 놓친 메시지를 대조할 때 쓴다.
 *
 * <p>{@code GET /api/matches/{matchId}/messages?before=&limit=} → {@code ChatService#getHistory}. 최신 메시지부터
 * {@code limit}개(기본 50, 최대 100). {@code before}를 주면 그 messageId보다 오래된 것만 준다(커서 방식
 * 페이지 넘기기 — "몇 페이지"가 아니라 "이 메시지 이전"으로 넘겨서, 그 사이 새 메시지가 와도 겹치거나 빠지지 않는다).</p>
 *
 * <p>클래스의 {@code @Validated}가 있어야 파라미터의 {@code @Positive}·{@code @Min}·{@code @Max}가 검사된다.
 * 실패하면 {@code ConstraintViolationException} → 400 {@code VALIDATION}.</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/matches/{matchId}/messages")
public class ChatRestController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long matchId,
            @RequestParam(required = false) @Positive Long before,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(chatService.getHistory(currentUser.userId(), matchId, before, limit));
    }
}
