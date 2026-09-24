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

/**
 * 슈퍼 계정 전용 열람 API. 모든 메서드가 첫 줄에서 {@code SuperAuthorization.requireSuper}로 역할을 확인한다
 * (관리자도 403 — {@code SuperAccountControllerTest#adminCannotListUsers}).
 *
 * <table>
 * <caption>API → 서비스</caption>
 * <tr><td>GET /api/super/users</td><td>{@link SuperAccountService#listUsers}</td><td>전체 계정 + 프로필</td></tr>
 * <tr><td>GET /api/super/chats</td><td>{@link SuperAccountService#listChats}</td><td>전체 채팅방 + 마지막 메시지</td></tr>
 * <tr><td>GET /api/super/chats/{matchId}/messages</td><td>{@link SuperAccountService#listMessages}</td><td>방 하나의 메시지(페이지)</td></tr>
 * </table>
 *
 * <p>클래스의 {@code @Validated}: 요청 본문이 아닌 {@code @RequestParam}에 붙은 {@code @Positive}·{@code @Min}·{@code @Max}는
 * 이게 있어야 검사된다. 어기면 {@code ConstraintViolationException}이 나고 {@code GlobalExceptionHandler}가 400으로 바꾼다.</p>
 */
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
