package com.facecook.cook.controller;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.service.UserActivityService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.common.exception.GlobalExceptionHandler;
import com.facecook.common.session.CurrentUserArgumentResolver;
import com.facecook.common.session.ActivityTrackingInterceptor;
import com.facecook.common.session.SessionAuthenticationInterceptor;
import com.facecook.common.session.SessionCookieService;
import com.facecook.common.session.SessionProperties;
import com.facecook.common.session.SessionTokenSigner;
import com.facecook.config.WebConfig;
import com.facecook.cook.dto.CookListResponse;
import com.facecook.cook.dto.CookUsageResponse;
import com.facecook.match.controller.MatchController;
import com.facecook.match.dto.MatchResponse;
import com.facecook.match.service.MatchService;
import com.facecook.cook.dto.SendCookResponse;
import com.facecook.cook.service.CookService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CookController.class, MatchController.class})
@Import({
        GlobalExceptionHandler.class,
        WebConfig.class,
        SessionAuthenticationInterceptor.class,
        ActivityTrackingInterceptor.class,
        CurrentUserArgumentResolver.class,
        SessionTokenSigner.class,
        SessionCookieService.class,
        CookControllerTest.SessionTestConfig.class
})
class CookControllerTest {
    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenSigner sessionTokenSigner;

    @MockitoBean
    private CookService cookService;

    @MockitoBean
    private MatchService matchService;

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
    void sendsCookForCurrentUser() throws Exception {
        givenAuthenticatedUser(1L);
        when(cookService.send(any(), any())).thenReturn(new SendCookResponse(
                10L,
                2L,
                "matched",
                LocalDateTime.of(2026, 9, 30, 12, 0),
                true,
                20L
        ));

        mockMvc.perform(post("/api/cooks")
                        .cookie(validCookie(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"receiverId": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiverId").value(2))
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.matchId").value(20));
    }

    @Test
    void rejectsCookWithoutReceiverId() throws Exception {
        givenAuthenticatedUser(1L);

        mockMvc.perform(post("/api/cooks")
                        .cookie(validCookie(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void returnsCookListsAndUsage() throws Exception {
        givenAuthenticatedUser(1L);
        when(cookService.getCooks(1L)).thenReturn(new CookListResponse(
                List.of(),
                List.of(),
                new CookUsageResponse(3L, 10, 8L)
        ));

        mockMvc.perform(get("/api/cooks").cookie(validCookie(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").isArray())
                .andExpect(jsonPath("$.received").isArray())
                .andExpect(jsonPath("$.usage.todayUsed").value(3))
                .andExpect(jsonPath("$.usage.dailyLimit").value(10))
                .andExpect(jsonPath("$.usage.totalUsed").value(8));
    }

    @Test
    void returnsMatchesAndMatchDetail() throws Exception {
        givenAuthenticatedUser(1L);
        MatchResponse response = new MatchResponse(
                20L,
                LocalDateTime.of(2026, 9, 30, 12, 0),
                null,
                null,
                0
        );
        when(matchService.getMatches(1L)).thenReturn(List.of(response));
        when(matchService.getMatch(1L, 20L)).thenReturn(response);

        mockMvc.perform(get("/api/matches").cookie(validCookie(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchId").value(20));

        mockMvc.perform(get("/api/matches/20").cookie(validCookie(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(20));
    }

    @Test
    void marksMatchReadForCurrentUser() throws Exception {
        givenAuthenticatedUser(1L);

        mockMvc.perform(patch("/api/matches/20/read").cookie(validCookie(1L)))
                .andExpect(status().isNoContent());

        verify(matchService).markRead(1L, 20L);
    }

    @Test
    void cancelsCookForCurrentUser() throws Exception {
        givenAuthenticatedUser(1L);

        mockMvc.perform(delete("/api/cooks/10").cookie(validCookie(1L)))
                .andExpect(status().isNoContent());

        verify(cookService).cancel(1L, 10L);
    }

    @Test
    void rejectsCancelOfNonSenderCook() throws Exception {
        givenAuthenticatedUser(1L);
        doThrow(new ApiException(ErrorCode.FORBIDDEN)).when(cookService).cancel(1L, 10L);

        mockMvc.perform(delete("/api/cooks/10").cookie(validCookie(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsCookLookupWithoutSession() throws Exception {
        mockMvc.perform(get("/api/cooks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void givenAuthenticatedUser(Long userId) {
        User user = User.createParticipant("user@example.com", LocalDateTime.of(2026, 9, 30, 12, 0));
        setId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private Cookie validCookie(Long userId) {
        return new Cookie(COOKIE_NAME, sessionTokenSigner.issue(userId, 604800));
    }

    private static void setId(User user, Long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
