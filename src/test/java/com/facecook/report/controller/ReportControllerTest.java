package com.facecook.report.controller;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserRole;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.service.UserActivityService;
import com.facecook.common.exception.GlobalExceptionHandler;
import com.facecook.common.session.CurrentUserArgumentResolver;
import com.facecook.common.session.ActivityTrackingInterceptor;
import com.facecook.common.session.SessionAuthenticationInterceptor;
import com.facecook.common.session.SessionCookieService;
import com.facecook.common.session.SessionProperties;
import com.facecook.common.session.SessionTokenSigner;
import com.facecook.config.WebConfig;
import com.facecook.report.dto.ReportResponse;
import com.facecook.report.service.ReportService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ReportController.class, AdminReportController.class})
@Import({
        GlobalExceptionHandler.class,
        WebConfig.class,
        SessionAuthenticationInterceptor.class,
        ActivityTrackingInterceptor.class,
        CurrentUserArgumentResolver.class,
        SessionTokenSigner.class,
        SessionCookieService.class,
        ReportControllerTest.SessionTestConfig.class
})
class ReportControllerTest {
    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");
    private static final LocalDateTime EVENT_NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenSigner sessionTokenSigner;

    @MockitoBean
    private ReportService reportService;

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
    void participantCreatesReport() throws Exception {
        givenAuthenticatedUser(1L, UserRole.PARTICIPANT);
        when(reportService.create(eq(1L), any())).thenReturn(response("pending"));

        mockMvc.perform(post("/api/reports")
                        .cookie(validCookie(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportedUserId":2,"reason":"욕설","detail":"상세"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(10))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void rejectsReportWithoutReason() throws Exception {
        givenAuthenticatedUser(1L, UserRole.PARTICIPANT);

        mockMvc.perform(post("/api/reports")
                        .cookie(validCookie(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reportedUserId":2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void adminReadsAndResolvesReport() throws Exception {
        givenAuthenticatedUser(7L, UserRole.ADMIN);
        when(reportService.getAll()).thenReturn(List.of(response("pending")));
        when(reportService.resolve(eq(10L), eq(7L), any())).thenReturn(response("reviewed"));

        mockMvc.perform(get("/api/admin/reports").cookie(validCookie(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportId").value(10));

        mockMvc.perform(post("/api/admin/reports/10/resolve")
                        .cookie(validCookie(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suspend":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reviewed"));
    }

    @Test
    void participantCannotUseAdminReportApi() throws Exception {
        givenAuthenticatedUser(1L, UserRole.PARTICIPANT);

        mockMvc.perform(get("/api/admin/reports").cookie(validCookie(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void resolveRequiresSuspendDecision() throws Exception {
        givenAuthenticatedUser(7L, UserRole.ADMIN);

        mockMvc.perform(post("/api/admin/reports/10/resolve")
                        .cookie(validCookie(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    private void givenAuthenticatedUser(Long userId, UserRole role) {
        User user = User.createParticipant("user" + userId + "@example.com", EVENT_NOW);
        setField(user, "id", userId);
        setField(user, "role", role);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private Cookie validCookie(Long userId) {
        return new Cookie(COOKIE_NAME, sessionTokenSigner.issue(userId, 604800));
    }

    private static ReportResponse response(String status) {
        return new ReportResponse(10L, 1L, 2L, "욕설", "상세", status, null, null, EVENT_NOW);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
