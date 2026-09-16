package com.facecook.superaccount.controller;

import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.common.session.SuperAuthorization;
import com.facecook.superaccount.dto.SuperChatRoomResponse;
import com.facecook.superaccount.dto.SuperUserResponse;
import com.facecook.superaccount.service.SuperAccountService;
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
@RequestMapping("/api/super")
public class SuperAccountController {

    private final SuperAccountService superAccountService;

    @GetMapping("/users")
    public ResponseEntity<List<SuperUserResponse>> listUsers(@CurrentUser AuthenticatedUser currentUser) {
        SuperAuthorization.requireSuper(currentUser);
        return ResponseEntity.ok(superAccountService.listUsers());
    }

    @GetMapping("/chats")
    public ResponseEntity<List<SuperChatRoomResponse>> listChats(@CurrentUser AuthenticatedUser currentUser) {
        SuperAuthorization.requireSuper(currentUser);
        return ResponseEntity.ok(superAccountService.listChats());
    }

    @GetMapping("/chats/{matchId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> listMessages(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long matchId,
            @RequestParam(required = false) @Positive Long before,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        SuperAuthorization.requireSuper(currentUser);
        return ResponseEntity.ok(superAccountService.listMessages(matchId, before, limit));
    }
}
