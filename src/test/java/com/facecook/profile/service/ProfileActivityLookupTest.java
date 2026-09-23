package com.facecook.profile.service;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.config.ProfileActivityProperties;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.entity.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileActivityLookupTest {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), EVENT_ZONE);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), EVENT_ZONE);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProfileActivityLookup activityLookup =
            new ProfileActivityLookup(userRepository, new ProfileActivityProperties(15), CLOCK);

    @Test
    void marksActiveWhenLastActiveWithinWindow() {
        Profile profile = profileWithLastActiveAt(1L, NOW.minusMinutes(5));

        ProfileResponse response = activityLookup.toResponse(profile);

        assertThat(response.isActive()).isTrue();
        assertThat(response.lastActiveAt()).isEqualTo(NOW.minusMinutes(5));
    }

    @Test
    void marksInactiveWhenLastActiveOutsideWindow() {
        Profile profile = profileWithLastActiveAt(1L, NOW.minusMinutes(20));

        ProfileResponse response = activityLookup.toResponse(profile);

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void marksInactiveWhenNeverActive() {
        Profile profile = profileWithLastActiveAt(1L, null);

        ProfileResponse response = activityLookup.toResponse(profile);

        assertThat(response.isActive()).isFalse();
        assertThat(response.lastActiveAt()).isNull();
    }

    @Test
    void resolvesLastActiveAtWhenUserAssociationMissing() {
        User user = User.createParticipant("signup@example.com", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "lastActiveAt", NOW.minusMinutes(5));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Profile profile = Profile.create(1L, new Profile.NewProfile(
                "닉네임", "female", 21, "ENFP", "요리", "A",
                null, null, null, null, null
        ));

        ProfileResponse response = activityLookup.toResponse(profile);

        assertThat(response.isActive()).isTrue();
        assertThat(response.lastActiveAt()).isEqualTo(NOW.minusMinutes(5));
    }

    @Test
    void buildsResponsesForEachProfilePreservingOrder() {
        Profile first = profileWithLastActiveAt(1L, NOW.minusMinutes(5));
        Profile second = profileWithLastActiveAt(2L, NOW.minusMinutes(30));

        List<ProfileResponse> responses = activityLookup.toResponses(List.of(first, second));

        assertThat(responses).extracting(ProfileResponse::userId).containsExactly(1L, 2L);
        assertThat(responses.get(0).isActive()).isTrue();
        assertThat(responses.get(1).isActive()).isFalse();
    }

    private Profile profileWithLastActiveAt(Long userId, LocalDateTime lastActiveAt) {
        User user = User.createParticipant(userId + "@example.com", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "lastActiveAt", lastActiveAt);

        Profile profile = Profile.create(userId, new Profile.NewProfile(
                "닉네임", "female", 21, "ENFP", "요리", "A",
                null, null, null, null, null
        ));
        ReflectionTestUtils.setField(profile, "user", user);
        return profile;
    }
}
