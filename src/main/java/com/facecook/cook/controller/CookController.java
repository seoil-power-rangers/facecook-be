package com.facecook.cook.controller;

import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.cook.dto.CookListResponse;
import com.facecook.cook.dto.SendCookRequest;
import com.facecook.cook.dto.SendCookResponse;
import com.facecook.cook.service.CookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cooks")
public class CookController {

    private final CookService cookService;

    @PostMapping
    public ResponseEntity<SendCookResponse> send(
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody SendCookRequest request
    ) {
        return ResponseEntity.ok(cookService.send(currentUser.userId(), request));
    }

    @GetMapping
    public ResponseEntity<CookListResponse> getCooks(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(cookService.getCooks(currentUser.userId()));
    }

    @DeleteMapping("/{cookId}")
    public ResponseEntity<Void> cancel(
            @CurrentUser AuthenticatedUser currentUser,
            @PathVariable Long cookId
    ) {
        cookService.cancel(currentUser.userId(), cookId);
        return ResponseEntity.noContent().build();
    }
}
