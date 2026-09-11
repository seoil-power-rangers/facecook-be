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
