package com.facecook.superaccount.controller;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserRole;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.service.UserActivityService;
import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.common.exception.GlobalExceptionHandler;
import com.facecook.common.session.ActivityTrackingInterceptor;
import com.facecook.common.session.CurrentUserArgumentResolver;
import com.facecook.common.session.SessionAuthenticationInterceptor;
import com.facecook.common.session.SessionCookieService;
import com.facecook.common.session.SessionProperties;
import com.facecook.common.session.SessionTokenSigner;
import com.facecook.config.WebConfig;
import com.facecook.superaccount.dto.SuperChatMemberResponse;
import com.facecook.superaccount.dto.SuperChatRoomResponse;
import com.facecook.superaccount.dto.SuperUserResponse;
import com.facecook.superaccount.service.SuperAccountService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuperAccountController.class)
@Import({
        GlobalExceptionHandler.class,
        WebConfig.class,
        SessionAuthenticationInterceptor.class,
        ActivityTrackingInterceptor.class,
        CurrentUserArgumentResolver.class,
        SessionTokenSigner.class,
        SessionCookieService.class,
        SuperAccountControllerTest.SessionTestConfig.class
})
class SuperAccountControllerTest {
    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");
    private static final LocalDateTime EVENT_NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenSigner sessionTokenSigner;

    @MockitoBean
    private SuperAccountService superAccountService;

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
    void superListsUsers() throws Exception {
        givenAuthenticatedUser(9L, UserRole.SUPER);
        when(superAccountService.listUsers()).thenReturn(List.of(
                new SuperUserResponse(
                        1L,
                        "user@example.com",
                        "participant",
                        "active",
                        EVENT_NOW,
                        EVENT_NOW,
                        "지호",
                        "남성",
                        24,
                        "ENFP",
                        "카페",
                        "A형",
                        "컴퓨터공학과",
                        "3학년",
                        "안녕",
                        "다정한 사람",
                        null
                )
        ));

        mockMvc.perform(get("/api/super/users").cookie(validCookie(9L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].email").value("user@example.com"))
                .andExpect(jsonPath("$[0].nickname").value("지호"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void superListsChats() throws Exception {
        givenAuthenticatedUser(9L, UserRole.SUPER);
        when(superAccountService.listChats()).thenReturn(List.of(
                new SuperChatRoomResponse(
                        42L,
                        EVENT_NOW,
                        new SuperChatMemberResponse(1L, "a@example.com", "지호", "남성", null),
                        new SuperChatMemberResponse(2L, "b@example.com", "수아", "여성", null),
                        "안녕",
                        EVENT_NOW
                )
        ));

        mockMvc.perform(get("/api/super/chats").cookie(validCookie(9L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchId").value(42))
                .andExpect(jsonPath("$[0].userA.nickname").value("지호"))
                .andExpect(jsonPath("$[0].lastMessage").value("안녕"));
    }

    @Test
    void superListsChatMessages() throws Exception {
        givenAuthenticatedUser(9L, UserRole.SUPER);
        when(superAccountService.listMessages(42L, null, 50)).thenReturn(List.of(
                new ChatMessageResponse(7L, 42L, 1L, "안녕", UUID.fromString("11111111-1111-1111-1111-111111111111"), EVENT_NOW)
        ));

        mockMvc.perform(get("/api/super/chats/42/messages").cookie(validCookie(9L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("안녕"));
    }

    @Test
    void adminCannotListUsers() throws Exception {
        givenAuthenticatedUser(7L, UserRole.ADMIN);

        mockMvc.perform(get("/api/super/users").cookie(validCookie(7L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void participantCannotListChats() throws Exception {
        givenAuthenticatedUser(1L, UserRole.PARTICIPANT);

        mockMvc.perform(get("/api/super/chats").cookie(validCookie(1L)))
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
