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
