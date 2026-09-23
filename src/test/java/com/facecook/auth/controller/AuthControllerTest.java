package com.facecook.auth.controller;

import com.facecook.auth.dto.AuthVerificationResponse;
import com.facecook.auth.dto.RequestCodeResponse;
import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.service.UserActivityService;
import com.facecook.auth.service.AuthService;
import com.facecook.common.exception.GlobalExceptionHandler;
import com.facecook.common.session.ActivityTrackingInterceptor;
import com.facecook.common.session.SessionAuthenticationInterceptor;
import com.facecook.common.session.SessionAuthenticator;
import com.facecook.common.session.SessionCookieService;
import com.facecook.common.session.SessionProperties;
import com.facecook.common.session.SessionTokenSigner;
import com.facecook.common.session.CurrentUserArgumentResolver;
import com.facecook.config.WebConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        GlobalExceptionHandler.class,
        WebConfig.class,
        SessionAuthenticationInterceptor.class,
        SessionAuthenticator.class,
        ActivityTrackingInterceptor.class,
        CurrentUserArgumentResolver.class,
        SessionTokenSigner.class,
        SessionCookieService.class,
        AuthControllerTest.SessionTestConfig.class
})
class AuthControllerTest {

    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenSigner sessionTokenSigner;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserActivityService userActivityService;

    @TestConfiguration
    static class SessionTestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        SessionProperties sessionProperties() {
            return new SessionProperties("test-secret", COOKIE_NAME, 604800, false, "Lax", null);
        }
    }

    @Test
    void requestsVerificationCode() throws Exception {
        when(authService.requestCode(any())).thenReturn(new RequestCodeResponse(300, 30));

        mockMvc.perform(post("/api/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "purpose": "signup"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendAfterSeconds").value(30));
    }

    @Test
    void returnsValidationErrorForInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "purpose": "signup"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void returnsValidationErrorForUnknownPurpose() throws Exception {
        mockMvc.perform(post("/api/auth/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "purpose": "reset-password"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void verifiesSignupResponseAndIssuesSessionCookie() throws Exception {
        when(authService.verifySignup(any())).thenReturn(
                new AuthVerificationResponse(1L, "user@example.com", "participant")
        );

        mockMvc.perform(post("/api/auth/verify-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "code": "123456",
                                  "password": "password123",
                                  "agreedTerms": ["service", "privacy"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.role").value("participant"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().httpOnly(COOKIE_NAME, true));
    }

    @Test
    void returnsValidationErrorForShortSignupPassword() throws Exception {
        mockMvc.perform(post("/api/auth/verify-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "code": "123456",
                                  "password": "short",
                                  "agreedTerms": ["service", "privacy"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void passwordLoginIssuesSessionCookie() throws Exception {
        when(authService.login(any())).thenReturn(
                new AuthVerificationResponse(1L, "user@example.com", "participant")
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("participant"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andExpect(cookie().httpOnly(COOKIE_NAME, true));
    }

    @Test
    void passwordLoginAcceptsNonEmailIdentifier() throws Exception {
        when(authService.login(any())).thenReturn(
                new AuthVerificationResponse(9L, "rhgustjrwkwlxjf", "super")
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "rhgustjrwkwlxjf",
                                  "password": "dlwlalsqhwlxjf"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("super"))
                .andExpect(jsonPath("$.email").value("rhgustjrwkwlxjf"));
    }

    @Test
    void meReturnsCurrentUserForValidSession() throws Exception {
        User user = User.createParticipant("user@example.com", NOW.atZone(ZoneOffset.UTC).toLocalDateTime());
        setId(user, 7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/me").cookie(validCookie(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("participant"));
    }

    @Test
    void meRejectsRequestWithoutSessionCookie() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meRejectsTamperedSessionCookie() throws Exception {
        Cookie tampered = validCookie(7L);
        tampered.setValue(tampered.getValue() + "tampered");

        mockMvc.perform(get("/api/auth/me").cookie(tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meRejectsSuspendedAccountAndClearsCookie() throws Exception {
        User user = User.createParticipant("user@example.com", NOW.atZone(ZoneOffset.UTC).toLocalDateTime());
        setId(user, 9L);
        user.suspend();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/me").cookie(validCookie(9L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUSPENDED"))
                .andExpect(cookie().maxAge(COOKIE_NAME, 0));
    }

    @Test
    void logoutClearsSessionCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE_NAME, 0));
    }

    @Test
    void allowsLocalFrontendCorsPreflight() throws Exception {
        for (String origin : new String[]{"http://localhost:3000", "http://localhost:3001"}) {
            mockMvc.perform(options("/api/auth/request-code")
                            .header(HttpHeaders.ORIGIN, origin)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }
    }

    @Test
    void allowsCorsPreflightOnSessionProtectedPath() throws Exception {
        // 세션 쿠키가 없는 preflight(OPTIONS)까지 인터셉터가 막으면, 브라우저가
        // 실제 요청(GET /me 등)을 보내기도 전에 CORS 에러로 처리해버린다.
        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    private Cookie validCookie(Long userId) {
        return new Cookie(COOKIE_NAME, sessionTokenSigner.issue(userId, 604800));
    }

    private static void setId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
