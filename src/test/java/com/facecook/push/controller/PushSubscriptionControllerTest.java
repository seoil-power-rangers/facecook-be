package com.facecook.push.controller;

import com.facecook.auth.entity.User;
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
import com.facecook.push.service.PushSubscriptionService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PushSubscriptionController.class)
@Import({
        GlobalExceptionHandler.class,
        WebConfig.class,
        SessionAuthenticationInterceptor.class,
        ActivityTrackingInterceptor.class,
        CurrentUserArgumentResolver.class,
        SessionTokenSigner.class,
        SessionCookieService.class,
        PushSubscriptionControllerTest.SessionTestConfig.class
})
class PushSubscriptionControllerTest {
    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenSigner sessionTokenSigner;

    @MockitoBean
    private PushSubscriptionService pushSubscriptionService;

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
    void subscribesCurrentUser() throws Exception {
        givenAuthenticatedUser(1L);

        mockMvc.perform(post("/api/push/subscribe")
                        .cookie(validCookie(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "endpoint":"https://push.example/subscription",
                                  "keys":{"p256dh":"p256dh-key","auth":"auth-key"}
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(pushSubscriptionService).subscribe(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    @Test
    void rejectsSubscriptionWithoutKeys() throws Exception {
        givenAuthenticatedUser(1L);

        mockMvc.perform(post("/api/push/subscribe")
                        .cookie(validCookie(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endpoint":"https://push.example/subscription"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void unsubscribesCurrentUser() throws Exception {
        givenAuthenticatedUser(1L);

        mockMvc.perform(delete("/api/push/subscribe").cookie(validCookie(1L)))
                .andExpect(status().isNoContent());

        verify(pushSubscriptionService).unsubscribe(1L);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/push/subscribe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void returnsVapidPublicKey() throws Exception {
        givenAuthenticatedUser(1L);
        when(pushSubscriptionService.getVapidPublicKey()).thenReturn("test-public-key");

        mockMvc.perform(get("/api/push/vapid-public-key").cookie(validCookie(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("test-public-key"));
    }

    @Test
    void vapidPublicKeyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/push/vapid-public-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void givenAuthenticatedUser(Long userId) {
        User user = User.createParticipant(
                "user" + userId + "@example.com",
                LocalDateTime.of(2026, 9, 30, 12, 0)
        );
        setField(user, "id", userId);
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
