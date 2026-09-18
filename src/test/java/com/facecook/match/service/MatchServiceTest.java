package com.facecook.match.service;

import com.facecook.chat.repository.MessageRepository;
import com.facecook.chat.repository.UnreadCountProjection;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.match.entity.MatchInfo;
import com.facecook.match.repository.MatchInfoRepository;
import com.facecook.match.repository.RecentMessageProjection;
import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
import com.facecook.profile.service.ProfileActivityLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");
    private static final LocalDateTime EVENT_NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    @Mock
    private MatchInfoRepository matchInfoRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ProfileActivityLookup activityLookup;

    @Mock
    private MessageRepository messageRepository;

    private MatchService matchService;

    @BeforeEach
    void setUp() {
        // 활동 정보(lastActiveAt/isActive)는 이 테스트의 관심사가 아니라, 실제
        // ProfileResponse.from을 그대로 위임해 null/false로 채운다.
        org.mockito.Mockito.lenient().when(activityLookup.toResponse(any(Profile.class)))
                .thenAnswer(invocation -> ProfileResponse.from(invocation.getArgument(0), null, false));
        org.mockito.Mockito.lenient().when(activityLookup.toResponses(any()))
                .thenAnswer(invocation -> {
                    List<Profile> profiles = invocation.getArgument(0);
                    return profiles.stream()
                            .map(profile -> ProfileResponse.from(profile, null, false))
                            .toList();
                });

        matchService = new MatchService(
                matchInfoRepository,
                profileRepository,
                activityLookup,
                messageRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void listsMatchesWithPartnerProfileAndRecentMessage() {
        MatchInfo match = match(20L, 1L, 2L, EVENT_NOW.minusMinutes(10));
        RecentMessageProjection message = recentMessage(20L, 2L, "안녕하세요", EVENT_NOW.minusMinutes(1));
        when(matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(1L, 1L))
                .thenReturn(List.of(match));
        when(profileRepository.findAllById(List.of(2L))).thenReturn(List.of(profile(2L, "partner")));
        when(matchInfoRepository.findRecentMessagesByMatchIds(List.of(20L))).thenReturn(List.of(message));

        var responses = matchService.getMatches(1L);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.matchId()).isEqualTo(20L);
            assertThat(response.partner().nickname()).isEqualTo("partner");
            assertThat(response.recentMessage().content()).isEqualTo("안녕하세요");
        });
    }

    @Test
    void batchesPartnerProfileLookupAcrossMatchesInsteadOfPerMatch() {
        MatchInfo matchWithUser2 = match(20L, 1L, 2L, EVENT_NOW.minusMinutes(10));
        MatchInfo matchWithUser3 = match(21L, 1L, 3L, EVENT_NOW.minusMinutes(5));
        when(matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(1L, 1L))
                .thenReturn(List.of(matchWithUser2, matchWithUser3));
        when(profileRepository.findAllById(List.of(2L, 3L)))
                .thenReturn(List.of(profile(2L, "two"), profile(3L, "three")));

        var responses = matchService.getMatches(1L);

        assertThat(responses).extracting(response -> response.partner().nickname())
                .containsExactly("two", "three");
        // 매칭이 2건이어도 상대 프로필 조회는 한 번(findAllById)만 나가야 한다 — N+1 회귀 방지.
        verify(profileRepository, never()).findById(any());
        verify(profileRepository).findAllById(List.of(2L, 3L));
    }

    @Test
    void batchesRecentMessageAndUnreadCountLookupAcrossMatchesInsteadOfPerMatch() {
        MatchInfo matchWithUser2 = match(20L, 1L, 2L, EVENT_NOW.minusMinutes(10));
        MatchInfo matchWithUser3 = match(21L, 1L, 3L, EVENT_NOW.minusMinutes(5));
        when(matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(1L, 1L))
                .thenReturn(List.of(matchWithUser2, matchWithUser3));
        when(profileRepository.findAllById(List.of(2L, 3L)))
                .thenReturn(List.of(profile(2L, "two"), profile(3L, "three")));

        matchService.getMatches(1L);

        // 매칭이 2건이어도 최근 메시지·안읽음 개수 조회는 각각 한 번만 나가야 한다 — N+1 회귀 방지.
        verify(matchInfoRepository, times(1)).findRecentMessagesByMatchIds(List.of(20L, 21L));
        verify(messageRepository, times(1)).findUnreadCountsByMatchIds(List.of(20L, 21L), 1L);
    }

    @Test
    void skipsBatchQueriesWhenThereAreNoMatches() {
        when(matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(1L, 1L))
                .thenReturn(List.of());

        var responses = matchService.getMatches(1L);

        assertThat(responses).isEmpty();
        verify(matchInfoRepository, never()).findRecentMessagesByMatchIds(any());
        verify(messageRepository, never()).findUnreadCountsByMatchIds(any(), any());
    }

    @Test
    void nonParticipantCannotReadMatchDetail() {
        when(matchInfoRepository.findById(20L)).thenReturn(Optional.of(match(20L, 1L, 2L, EVENT_NOW)));

        assertErrorCode(() -> matchService.getMatch(3L, 20L), ErrorCode.FORBIDDEN);

        verify(profileRepository, never()).findById(any());
    }

    @Test
    void unreadCountReflectsWhatTheBatchQueryReturns() {
        // lastReadAt 기준 필터링은 이제 findUnreadCountsByMatchIds 쿼리 안에서
        // 끝난다(match_info의 user_a/b_last_read_at을 직접 조인) — 서비스는
        // 쿼리가 돌려준 매칭별 개수를 그대로 응답에 옮기기만 하면 된다.
        MatchInfo match = match(20L, 1L, 2L, EVENT_NOW.minusMinutes(10));
        when(matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(1L, 1L))
                .thenReturn(List.of(match));
        when(profileRepository.findAllById(List.of(2L))).thenReturn(List.of(profile(2L, "partner")));
        when(messageRepository.findUnreadCountsByMatchIds(List.of(20L), 1L))
                .thenReturn(List.of(unreadCount(20L, 3L)));

        var responses = matchService.getMatches(1L);

        assertThat(responses).singleElement().satisfies(response ->
                assertThat(response.unreadCount()).isEqualTo(3L)
        );
    }

    @Test
    void unreadCountDefaultsToZeroWhenMatchHasNoUnreadRow() {
        // 안읽음이 0건인 매칭은 GROUP BY 결과에 행 자체가 안 나온다 — 맵에
        // 없는 매칭은 0으로 채워야 한다.
        MatchInfo match = match(20L, 1L, 2L, EVENT_NOW.minusMinutes(10));
        when(matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(1L, 1L))
                .thenReturn(List.of(match));
        when(profileRepository.findAllById(List.of(2L))).thenReturn(List.of(profile(2L, "partner")));
        when(messageRepository.findUnreadCountsByMatchIds(List.of(20L), 1L)).thenReturn(List.of());

        var responses = matchService.getMatches(1L);

        assertThat(responses).singleElement().satisfies(response ->
                assertThat(response.unreadCount()).isEqualTo(0L)
        );
    }

    @Test
    void markReadRejectsMissingMatch() {
        when(matchInfoRepository.findById(20L)).thenReturn(Optional.empty());

        assertErrorCode(() -> matchService.markRead(1L, 20L), ErrorCode.NOT_FOUND);
    }

    @Test
    void markReadRejectsNonParticipant() {
        MatchInfo match = match(20L, 1L, 2L, EVENT_NOW);
        when(matchInfoRepository.findById(20L)).thenReturn(Optional.of(match));

        assertErrorCode(() -> matchService.markRead(3L, 20L), ErrorCode.FORBIDDEN);

        assertThat(match.lastReadAt(1L)).isNull();
        assertThat(match.lastReadAt(2L)).isNull();
    }

    @Test
    void markReadUpdatesLastReadTimeForParticipant() {
        MatchInfo match = match(20L, 1L, 2L, EVENT_NOW.minusMinutes(10));
        when(matchInfoRepository.findById(20L)).thenReturn(Optional.of(match));

        matchService.markRead(1L, 20L);

        assertThat(match.lastReadAt(1L)).isEqualTo(EVENT_NOW);
        assertThat(match.lastReadAt(2L)).isNull();
    }

    private static MatchInfo match(Long id, Long firstUserId, Long secondUserId, LocalDateTime matchedAt) {
        MatchInfo matchInfo = MatchInfo.create(firstUserId, secondUserId, matchedAt);
        setField(matchInfo, "id", id);
        return matchInfo;
    }

    private static Profile profile(Long userId, String nickname) {
        return Profile.create(userId, new CreateProfileRequest(
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
        ));
    }

    private static RecentMessageProjection recentMessage(Long matchId, Long senderId, String content, LocalDateTime sentAt) {
        return new RecentMessageProjection() {
            @Override
            public Long getMatchId() {
                return matchId;
            }

            @Override
            public Long getSenderId() {
                return senderId;
            }

            @Override
            public String getContent() {
                return content;
            }

            @Override
            public LocalDateTime getSentAt() {
                return sentAt;
            }
        };
    }

    private static UnreadCountProjection unreadCount(Long matchId, Long count) {
        return new UnreadCountProjection() {
            @Override
            public Long getMatchId() {
                return matchId;
            }

            @Override
            public Long getUnreadCount() {
                return count;
            }
        };
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

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected)
                );
    }
}
