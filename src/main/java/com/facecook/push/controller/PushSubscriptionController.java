package com.facecook.push.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.push.dto.VapidPublicKeyResponse;
import com.facecook.push.service.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 웹 푸시 구독 API. 발송 API는 없다 — 콕·매칭·메시지가 생길 때 서버가 스스로 보낸다({@code ParticipantPushNotificationService}).
 *
 * <table>
 * <tr><th>API</th><th>서비스</th><th>쓰는 화면(FE)</th></tr>
 * <tr><td>GET /api/push/vapid-public-key</td><td>{@link PushSubscriptionService#getVapidPublicKey}</td><td>알림 켜기(구독 만들기 전)</td></tr>
 * <tr><td>POST /api/push/subscribe</td><td>{@link PushSubscriptionService#subscribe}</td><td>알림 켜기</td></tr>
 * <tr><td>DELETE /api/push/subscribe</td><td>{@link PushSubscriptionService#unsubscribe}</td><td>알림 끄기</td></tr>
 * </table>
 *
 * <p>알림 켜기 순서(FE {@code notificationApi.ts}): 공개키 받기 → 브라우저에 구독 만들기
 * ({@code pushManager.subscribe}) → 그 결과(endpoint, keys)를 서버에 등록.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/push")
public class PushSubscriptionController {
    private final PushSubscriptionService pushSubscriptionService;

    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidPublicKeyResponse> getVapidPublicKey() {
        return ResponseEntity.ok(new VapidPublicKeyResponse(pushSubscriptionService.getVapidPublicKey()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        pushSubscriptionService.subscribe(currentUser.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<Void> unsubscribe(@CurrentUser AuthenticatedUser currentUser) {
        pushSubscriptionService.unsubscribe(currentUser.userId());
        return ResponseEntity.noContent().build();
    }
}
