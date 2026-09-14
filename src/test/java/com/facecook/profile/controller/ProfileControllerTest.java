package com.facecook.profile.controller;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.common.exception.GlobalExceptionHandler;
import com.facecook.common.session.CurrentUserArgumentResolver;
import com.facecook.common.session.SessionAuthenticationInterceptor;
import com.facecook.common.session.SessionCookieService;
import com.facecook.common.session.SessionProperties;
import com.facecook.common.session.SessionTokenSigner;
import com.facecook.config.WebConfig;
import com.facecook.profile.dto.PhotoUploadUrlResponse;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.service.ProfilePhotoUploadService;
import com.facecook.profile.service.ProfileService;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import({
        GlobalExceptionHandler.class,
        WebConfig.class,
        SessionAuthenticationInterceptor.class,
        CurrentUserArgumentResolver.class,
        SessionTokenSigner.class,
        SessionCookieService.class,
        ProfileControllerTest.SessionTestConfig.class
})
class ProfileControllerTest {

    private static final String COOKIE_NAME = "FACECOOK_SESSION";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Long CURRENT_USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTokenSigner sessionTokenSigner;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private ProfilePhotoUploadService profilePhotoUploadService;

    @MockitoBean
    private UserRepository userRepository;

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
    void createsProfileForCurrentUser() throws Exception {
        authenticateCurrentUser();
        when(profileService.create(any(), any())).thenReturn(profile(CURRENT_USER_ID, "cook"));

        mockMvc.perform(post("/api/profile")
                        .cookie(validCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "cook",
                                  "gender": "female",
                                  "age": 21,
                                  "mbti": "ENFP",
                                  "hobby": "요리",
                                  "bloodType": "A",
                                  "department": "컴퓨터공학과",
                                  "grade": "2학년",
                                  "bio": "안녕하세요",
                                  "idealType": "다정한 사람",
                                  "photo": "https://example.com/photo.jpg"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(CURRENT_USER_ID))
                .andExpect(jsonPath("$.nickname").value("cook"))
                .andExpect(jsonPath("$.bloodType").value("A"))
                .andExpect(jsonPath("$.photo").value("https://example.com/photo.jpg"));

        verify(profileService).create(org.mockito.ArgumentMatchers.eq(CURRENT_USER_ID), any());
    }

    @Test
    void rejectsProfileCreationWhenRequiredFieldIsMissing() throws Exception {
        authenticateCurrentUser();

        mockMvc.perform(post("/api/profile")
                        .cookie(validCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gender": "female",
                                  "age": 21,
                                  "mbti": "ENFP",
                                  "hobby": "요리",
                                  "bloodType": "A"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    @Test
    void getsCurrentUserProfile() throws Exception {
        authenticateCurrentUser();
        when(profileService.get(CURRENT_USER_ID)).thenReturn(profile(CURRENT_USER_ID, "cook"));

        mockMvc.perform(get("/api/profile").cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(CURRENT_USER_ID));
    }

    @Test
    void updatesOnlyEditableProfileFields() throws Exception {
        authenticateCurrentUser();
        when(profileService.update(any(), any())).thenReturn(profile(CURRENT_USER_ID, "cook"));

        mockMvc.perform(patch("/api/profile")
                        .cookie(validCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "department": "소프트웨어공학과",
                                  "bio": "수정된 소개"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("cook"));

        verify(profileService).update(org.mockito.ArgumentMatchers.eq(CURRENT_USER_ID), any());
    }

    @Test
    void listsParticipantsExceptCurrentUser() throws Exception {
        authenticateCurrentUser();
        when(profileService.getParticipantsExcept(CURRENT_USER_ID))
                .thenReturn(List.of(profile(8L, "other")));

        mockMvc.perform(get("/api/profiles").cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(8L))
                .andExpect(jsonPath("$[0].nickname").value("other"));
    }

    @Test
    void getsParticipantProfileByUserId() throws Exception {
        authenticateCurrentUser();
        when(profileService.get(8L)).thenReturn(profile(8L, "other"));

        mockMvc.perform(get("/api/profiles/8").cookie(validCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(8L));
    }

    @Test
    void issuesPhotoUploadUrlForCurrentUser() throws Exception {
        authenticateCurrentUser();
        when(profilePhotoUploadService.issueUploadUrl("image/jpeg"))
                .thenReturn(new PhotoUploadUrlResponse(
                        "https://facecook-photos.s3.ap-northeast-2.amazonaws.com/profile-photos/abc.jpg?X-Amz-Signature=...",
                        "https://facecook-photos.s3.ap-northeast-2.amazonaws.com/profile-photos/abc.jpg"
                ));

        mockMvc.perform(post("/api/profile/photo/upload-url")
                        .cookie(validCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentType": "image/jpeg"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value(
                        "https://facecook-photos.s3.ap-northeast-2.amazonaws.com/profile-photos/abc.jpg"
                ));
    }

    @Test
    void rejectsRequestWithoutSession() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private void authenticateCurrentUser() {
        User user = User.createParticipant("user@example.com", LocalDateTime.now());
        setId(user, CURRENT_USER_ID);
        when(userRepository.findById(CURRENT_USER_ID)).thenReturn(Optional.of(user));
    }

    private Cookie validCookie() {
        return new Cookie(COOKIE_NAME, sessionTokenSigner.issue(CURRENT_USER_ID, 604800));
    }

    private ProfileResponse profile(Long userId, String nickname) {
        return new ProfileResponse(
                userId,
                nickname,
                "female",
                21,
                "ENFP",
                "요리",
                "A",
                "컴퓨터공학과",
                "2학년",
                "안녕하세요",
                "다정한 사람",
                "https://example.com/photo.jpg"
        );
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
