package com.facecook.push.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.push.service.PushSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/push/subscribe")
public class PushSubscriptionController {
    private final PushSubscriptionService pushSubscriptionService;

    @PostMapping
    public ResponseEntity<Void> subscribe(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        pushSubscriptionService.subscribe(currentUser.userId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(@CurrentUser AuthenticatedUser currentUser) {
        pushSubscriptionService.unsubscribe(currentUser.userId());
        return ResponseEntity.noContent().build();
    }
}
