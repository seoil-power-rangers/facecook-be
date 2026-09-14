package com.facecook.chat.controller;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.service.UserActivityService;
import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.service.ChatService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRestController.class)
@Import({
        GlobalExceptionHandler.class,
        WebConfig.class,
        SessionAuthenticationInterceptor.class,
        ActivityTrackingInterceptor.class,
        CurrentUserArgumentResolver.class,
        SessionTokenSigner.class,
        SessionCookieService.class,
        ChatRestControllerTest.SessionTestConfig.class
})
class ChatRestControllerTest {

    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");
    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenSigner sessionTokenSigner;

    @MockitoBean
    private ChatService chatService;

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
    void returnsMessageHistoryWithDefaultLimit() throws Exception {
        authenticate();
        UUID clientMessageId = UUID.fromString("32fa481f-e623-49c3-9fa4-b47bf3dc84cc");
        when(chatService.getHistory(USER_ID, 20L, null, 50)).thenReturn(List.of(
                new ChatMessageResponse(
                        101L,
                        20L,
                        USER_ID,
                        "안녕하세요",
                        clientMessageId,
                        LocalDateTime.of(2026, 9, 30, 12, 0)
                )
        ));

        mockMvc.perform(get("/api/matches/20/messages").cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value(101))
                .andExpect(jsonPath("$[0].matchId").value(20))
                .andExpect(jsonPath("$[0].senderId").value(USER_ID))
                .andExpect(jsonPath("$[0].content").value("안녕하세요"))
                .andExpect(jsonPath("$[0].clientMessageId").value(clientMessageId.toString()));
    }

    @Test
    void forwardsBeforeAndLimit() throws Exception {
        authenticate();
        when(chatService.getHistory(USER_ID, 20L, 100L, 25)).thenReturn(List.of());

        mockMvc.perform(get("/api/matches/20/messages")
                        .param("before", "100")
                        .param("limit", "25")
                        .cookie(validCookie()))
                .andExpect(status().isOk());

        verify(chatService).getHistory(USER_ID, 20L, 100L, 25);
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        authenticate();

        mockMvc.perform(get("/api/matches/20/messages")
                        .param("before", "0")
                        .param("limit", "101")
                        .cookie(validCookie()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void rejectsHistoryWithoutSession() throws Exception {
        mockMvc.perform(get("/api/matches/20/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void authenticate() {
        User user = User.createParticipant("chat@example.com", LocalDateTime.of(2026, 9, 30, 12, 0));
        setId(user, USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private Cookie validCookie() {
        return new Cookie(COOKIE_NAME, sessionTokenSigner.issue(USER_ID, 604800));
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
