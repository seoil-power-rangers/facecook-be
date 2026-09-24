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

/**
 * 가입·로그인 API({@code /api/auth/**}).
 *
 * <table>
 * <tr><th>API</th><th>서비스</th><th>세션 쿠키</th></tr>
 * <tr><td>POST /request-code</td><td>{@link AuthService#requestCode}</td><td>-</td></tr>
 * <tr><td>POST /verify-signup</td><td>{@link AuthService#verifyCodeAndCreateParticipant}</td><td>발급</td></tr>
 * <tr><td>POST /verify-login</td><td>{@link AuthService#verifyLogin}</td><td>발급</td></tr>
 * <tr><td>POST /login</td><td>{@link AuthService#login}</td><td>발급</td></tr>
 * <tr><td>GET /me</td><td>(서비스 없음, 세션의 사용자 정보 그대로)</td><td>필요</td></tr>
 * <tr><td>POST /logout</td><td>(서비스 없음)</td><td>삭제</td></tr>
 * </table>
 *
 * <p>쿠키 발급·삭제를 서비스가 아니라 여기서 하는 이유: 쿠키는 HTTP 응답의 일이라
 * 서비스가 {@code HttpServletResponse}를 알 필요가 없게 했다. 서비스는 "누가 인증됐는지"만
 * 돌려주고, 컨트롤러가 그 userId로 쿠키를 붙인다.</p>
 *
 * <p>{@code @Valid @RequestBody}: 요청 JSON을 record로 바꾼 뒤 필드의 검증 어노테이션
 * ({@code @NotBlank}, {@code @Email} 등)을 검사한다. 실패하면 컨트롤러 본문이 실행되지
 * 않고 {@code GlobalExceptionHandler}가 400 {@code VALIDATION}으로 응답한다.</p>
 *
 * <p>{@code /me}를 뺀 나머지({@code /logout} 포함)는 {@code WebConfig.PUBLIC_AUTH_PATHS}에
 * 들어 있어 세션 확인 없이 부를 수 있다. 그래서 로그아웃은 세션이 이미 만료된 상태에서도
 * 쿠키를 지울 수 있다.</p>
 */
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
        AuthVerificationResponse verification = authService.verifyCodeAndCreateParticipant(request);
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
