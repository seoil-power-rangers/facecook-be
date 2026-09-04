package com.facecook.auth.controller;

import com.facecook.auth.dto.AuthVerificationResponse;
import com.facecook.auth.dto.RequestCodeRequest;
import com.facecook.auth.dto.RequestCodeResponse;
import com.facecook.auth.dto.VerifyLoginRequest;
import com.facecook.auth.dto.VerifySignupRequest;
import com.facecook.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/request-code")
    public ResponseEntity<RequestCodeResponse> requestCode(@Valid @RequestBody RequestCodeRequest request) {
        return ResponseEntity.ok(authService.requestCode(request));
    }

    @PostMapping("/verify-signup")
    public ResponseEntity<AuthVerificationResponse> verifySignup(
            @Valid @RequestBody VerifySignupRequest request
    ) {
        return ResponseEntity.ok(authService.verifySignup(request));
    }

    @PostMapping("/verify-login")
    public ResponseEntity<AuthVerificationResponse> verifyLogin(
            @Valid @RequestBody VerifyLoginRequest request
    ) {
        return ResponseEntity.ok(authService.verifyLogin(request));
    }
}
