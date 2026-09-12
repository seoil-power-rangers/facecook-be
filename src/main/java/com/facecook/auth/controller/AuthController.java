package com.facecook.auth.controller;

import com.facecook.auth.dto.AuthVerificationResponse;
import com.facecook.auth.dto.PasswordLoginRequest;
import com.facecook.auth.dto.RequestCodeRequest;
import com.facecook.auth.dto.RequestCodeResponse;
import com.facecook.auth.dto.VerifyLoginRequest;
import com.facecook.auth.dto.VerifySignupRequest;
import com.facecook.auth.service.AuthService;
import com.facecook.common.session.AuthenticatedUser;
import com.facecook.common.session.CurrentUser;
import com.facecook.common.session.SessionCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionCookieService sessionCookieService;

    @PostMapping("/request-code")
    public ResponseEntity<RequestCodeResponse> requestCode(@Valid @RequestBody RequestCodeRequest request) {
        return ResponseEntity.ok(authService.requestCode(request));
    }

    @PostMapping("/verify-signup")
    public ResponseEntity<AuthVerificationResponse> verifySignup(
            @Valid @RequestBody VerifySignupRequest request,
            HttpServletResponse response
    ) {
        AuthVerificationResponse verification = authService.verifySignup(request);
        sessionCookieService.issue(response, verification.userId());
        return ResponseEntity.ok(verification);
    }

    @PostMapping("/verify-login")
    public ResponseEntity<AuthVerificationResponse> verifyLogin(
            @Valid @RequestBody VerifyLoginRequest request,
            HttpServletResponse response
    ) {
        AuthVerificationResponse verification = authService.verifyLogin(request);
        sessionCookieService.issue(response, verification.userId());
        return ResponseEntity.ok(verification);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthVerificationResponse> login(
            @Valid @RequestBody PasswordLoginRequest request,
            HttpServletResponse response
    ) {
        AuthVerificationResponse verification = authService.login(request);
        sessionCookieService.issue(response, verification.userId());
        return ResponseEntity.ok(verification);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthVerificationResponse> me(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(AuthVerificationResponse.from(currentUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        sessionCookieService.clear(response);
        return ResponseEntity.noContent().build();
    }
}
