package com.facecook.admin.controller;

import com.facecook.admin.dto.AdminStatsResponse;
import com.facecook.admin.service.AdminStatsService;
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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminStatsController.class)
@Import({
        GlobalExceptionHandler.class,
        WebConfig.class,
        SessionAuthenticationInterceptor.class,
        ActivityTrackingInterceptor.class,
        CurrentUserArgumentResolver.class,
        SessionTokenSigner.class,
        SessionCookieService.class,
        AdminStatsControllerTest.SessionTestConfig.class
})
class AdminStatsControllerTest {
    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");
    private static final LocalDateTime EVENT_NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenSigner sessionTokenSigner;

    @MockitoBean
    private AdminStatsService adminStatsService;

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
    void adminGetsStats() throws Exception {
        givenAuthenticatedUser(7L, UserRole.ADMIN);
        when(adminStatsService.getStats()).thenReturn(new AdminStatsResponse(214, 200, 487, 63, 21, 2));

        mockMvc.perform(get("/api/admin/stats").cookie(validCookie(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(214))
                .andExpect(jsonPath("$.activeToday").value(200))
                .andExpect(jsonPath("$.totalCooks").value(487))
                .andExpect(jsonPath("$.totalMatches").value(63))
                .andExpect(jsonPath("$.missionCleared").value(21))
                .andExpect(jsonPath("$.pendingReports").value(2));
    }

    @Test
    void participantCannotGetStats() throws Exception {
        givenAuthenticatedUser(1L, UserRole.PARTICIPANT);

        mockMvc.perform(get("/api/admin/stats").cookie(validCookie(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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
