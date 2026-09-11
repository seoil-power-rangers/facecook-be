package com.facecook.profile.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.dto.UpdateProfileRequest;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(profileRepository);
    }

    @Test
    void createsProfileForUser() {
        when(profileRepository.existsById(1L)).thenReturn(false);
        when(profileRepository.saveAndFlush(any(Profile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse response = profileService.create(1L, createRequest("cook"));

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("cook");
        assertThat(response.idealType()).isEqualTo("다정한 사람");
        verify(profileRepository).saveAndFlush(any(Profile.class));
    }

    @Test
    void rejectsDuplicateProfileCreation() {
        when(profileRepository.existsById(1L)).thenReturn(true);

        assertErrorCode(
                () -> profileService.create(1L, createRequest("cook")),
                ErrorCode.PROFILE_ALREADY_EXISTS
        );
        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void getsProfileByUserId() {
        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile(1L, "cook")));

        ProfileResponse response = profileService.get(1L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.nickname()).isEqualTo("cook");
    }

    @Test
    void rejectsMissingProfile() {
        when(profileRepository.findById(1L)).thenReturn(Optional.empty());

        assertErrorCode(() -> profileService.get(1L), ErrorCode.PROFILE_NOT_FOUND);
    }

    @Test
    void updatesEditableFieldsWithoutChangingRequiredFields() {
        Profile profile = profile(1L, "cook");
        when(profileRepository.findById(1L)).thenReturn(Optional.of(profile));

        ProfileResponse response = profileService.update(
                1L,
                new UpdateProfileRequest("소프트웨어공학과", null, "수정된 소개", null)
        );

        assertThat(response.nickname()).isEqualTo("cook");
        assertThat(response.department()).isEqualTo("소프트웨어공학과");
        assertThat(response.bio()).isEqualTo("수정된 소개");
        assertThat(response.grade()).isEqualTo("2학년");
    }

    @Test
    void rejectsUpdateWithoutAnyFields() {
        assertErrorCode(
                () -> profileService.update(1L, new UpdateProfileRequest(null, null, null, null)),
                ErrorCode.VALIDATION
        );
        verify(profileRepository, never()).findById(any());
    }

    @Test
    void listsProfilesExceptCurrentUser() {
        when(profileRepository.findAllByUserIdNotOrderByUserIdAsc(1L))
                .thenReturn(List.of(profile(2L, "two"), profile(3L, "three")));

        List<ProfileResponse> responses = profileService.getParticipantsExcept(1L);

        assertThat(responses).extracting(ProfileResponse::userId).containsExactly(2L, 3L);
        verify(profileRepository).findAllByUserIdNotOrderByUserIdAsc(1L);
    }

    private Profile profile(Long userId, String nickname) {
        return Profile.create(userId, createRequest(nickname));
    }

    private CreateProfileRequest createRequest(String nickname) {
        return new CreateProfileRequest(
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

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected)
                );
    }
}
