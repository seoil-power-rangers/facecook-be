package com.facecook.cook.service;

import com.facecook.auth.entity.User;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.dto.SendCookRequest;
import com.facecook.cook.entity.Cook;
import com.facecook.cook.entity.CookStatus;
import com.facecook.match.entity.MatchInfo;
import com.facecook.cook.repository.CookRepository;
import com.facecook.cook.repository.CookUserRepository;
import com.facecook.match.repository.MatchInfoRepository;
import com.facecook.profile.dto.CreateProfileRequest;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
import com.facecook.profile.service.ProfileActivityLookup;
import com.facecook.push.service.ParticipantPushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CookServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");
    private static final LocalDateTime EVENT_NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    @Mock
    private CookRepository cookRepository;

    @Mock
    private MatchInfoRepository matchInfoRepository;

    @Mock
    private CookUserRepository cookUserRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private ProfileActivityLookup activityLookup;

    @Mock
    private ParticipantPushNotificationService pushNotificationService;

    private CookService cookService;

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

        cookService = new CookService(
                cookRepository,
                matchInfoRepository,
                cookUserRepository,
                profileRepository,
                activityLookup,
                pushNotificationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void rejectsCookToSelf() {
        assertErrorCode(() -> cookService.send(1L, new SendCookRequest(1L)), ErrorCode.SELF);

        verify(cookUserRepository, never()).findAllByIdForUpdate(anyCollection());
    }

    @Test
    void rejectsMissingReceiver() {
        when(cookUserRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(user(1L)));

        assertErrorCode(() -> cookService.send(1L, new SendCookRequest(2L)), ErrorCode.NOT_FOUND);
    }

    @Test
    void rejectsAlreadyMatchedReceiverBeforeDuplicateCheck() {
        givenLockedUsers(1L, 2L);
        when(matchInfoRepository.existsBetween(1L, 2L)).thenReturn(true);

        assertErrorCode(() -> cookService.send(1L, new SendCookRequest(2L)), ErrorCode.ALREADY_MATCHED);

        verify(cookRepository, never()).existsBySenderIdAndReceiverId(any(), any());
    }

    @Test
    void rejectsDuplicateCook() {
        givenLockedUsers(1L, 2L);
        when(cookRepository.existsBySenderIdAndReceiverId(1L, 2L)).thenReturn(true);

        assertErrorCode(() -> cookService.send(1L, new SendCookRequest(2L)), ErrorCode.DUPLICATE);

        verify(cookRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsEleventhCookOfTheDay() {
        givenLockedUsers(1L, 2L);
        when(cookRepository.countBySenderIdAndSentAtGreaterThanEqualAndSentAtLessThan(
                1L,
                LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)
        )).thenReturn(10L);

        assertErrorCode(() -> cookService.send(1L, new SendCookRequest(2L)), ErrorCode.DAILY_LIMIT);

        verify(cookRepository, never()).saveAndFlush(any());
    }

    @Test
    void allowsTenthCookOfTheDay() {
        givenLockedUsers(1L, 2L);
        when(cookRepository.countBySenderIdAndSentAtGreaterThanEqualAndSentAtLessThan(
                1L,
                LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)
        )).thenReturn(9L);
        when(cookRepository.saveAndFlush(any(Cook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = cookService.send(1L, new SendCookRequest(2L));

        assertThat(response.status()).isEqualTo("pending");
        verify(cookRepository).saveAndFlush(any(Cook.class));
        verify(pushNotificationService).cookReceived(2L);
    }

    @Test
    void rejectsCookWhenEventWideDailyLimitReached() {
        givenLockedUsers(1L, 2L);
        when(cookRepository.countBySentAtGreaterThanEqualAndSentAtLessThan(
                LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)
        )).thenReturn(3_000L);

        assertErrorCode(() -> cookService.send(1L, new SendCookRequest(2L)), ErrorCode.EVENT_LIMIT);

        verify(cookRepository, never()).saveAndFlush(any());
    }

    @Test
    void allowsCookJustBelowEventWideDailyLimit() {
        givenLockedUsers(1L, 2L);
        when(cookRepository.countBySentAtGreaterThanEqualAndSentAtLessThan(
                LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)
        )).thenReturn(2_999L);
        when(cookRepository.saveAndFlush(any(Cook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = cookService.send(1L, new SendCookRequest(2L));

        assertThat(response.status()).isEqualTo("pending");
    }

    @Test
    void eventWideDailyLimitGrowsWithEventDay() {
        Clock secondDay = Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneOffset.UTC);
        CookService secondDayService = new CookService(
                cookRepository, matchInfoRepository, cookUserRepository, profileRepository,
                activityLookup, pushNotificationService, secondDay
        );
        givenLockedUsers(1L, 2L);
        when(cookRepository.countBySentAtGreaterThanEqualAndSentAtLessThan(
                LocalDateTime.of(2026, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 2, 0, 0)
        )).thenReturn(6_000L);

        assertErrorCode(() -> secondDayService.send(1L, new SendCookRequest(2L)), ErrorCode.EVENT_LIMIT);
    }

    @Test
    void eventWideDailyLimitDoesNotApplyOutsideEventWindow() {
        Clock beforeEvent = Clock.fixed(Instant.parse("2026-01-01T03:00:00Z"), ZoneOffset.UTC);
        CookService devService = new CookService(
                cookRepository, matchInfoRepository, cookUserRepository, profileRepository,
                activityLookup, pushNotificationService, beforeEvent
        );
        givenLockedUsers(1L, 2L);
        when(cookRepository.saveAndFlush(any(Cook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = devService.send(1L, new SendCookRequest(2L));

        assertThat(response.status()).isEqualTo("pending");
        verify(cookRepository, never()).countBySentAtGreaterThanEqualAndSentAtLessThan(any(), any());
    }

    @Test
    void countsDailyUsageBetweenKoreaMidnights() {
        givenLockedUsers(1L, 2L);
        when(cookRepository.saveAndFlush(any(Cook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        cookService.send(1L, new SendCookRequest(2L));

        verify(cookRepository).countBySenderIdAndSentAtGreaterThanEqualAndSentAtLessThan(
                1L,
                LocalDateTime.of(2026, 9, 30, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0)
        );
    }

    @Test
    void mutualCookCreatesMatchAndConnectsBothCooks() {
        givenLockedUsers(1L, 2L);
        Cook reverse = cook(10L, 2L, 1L, EVENT_NOW.minusMinutes(59));
        when(cookRepository.findBySenderIdAndReceiverId(2L, 1L)).thenReturn(Optional.of(reverse));
        when(cookRepository.saveAndFlush(any(Cook.class))).thenAnswer(invocation -> {
            Cook saved = invocation.getArgument(0);
            setField(saved, "id", 11L);
            return saved;
        });
        when(matchInfoRepository.saveAndFlush(any(MatchInfo.class))).thenAnswer(invocation -> {
            MatchInfo saved = invocation.getArgument(0);
            setField(saved, "id", 20L);
            return saved;
        });

        var response = cookService.send(1L, new SendCookRequest(2L));

        assertThat(response.matched()).isTrue();
        assertThat(response.matchId()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo("matched");
        assertThat(reverse.getStatus()).isEqualTo(CookStatus.MATCHED);
        assertThat(reverse.getMatchId()).isEqualTo(20L);

        ArgumentCaptor<MatchInfo> matchCaptor = ArgumentCaptor.forClass(MatchInfo.class);
        verify(matchInfoRepository).saveAndFlush(matchCaptor.capture());
        assertThat(matchCaptor.getValue().getUserAId()).isEqualTo(1L);
        assertThat(matchCaptor.getValue().getUserBId()).isEqualTo(2L);
        assertThat(matchCaptor.getValue().getMatchedAt()).isEqualTo(EVENT_NOW);
        verify(pushNotificationService).matchCreated(1L, 20L);
        verify(pushNotificationService).matchCreated(2L, 20L);
        verify(pushNotificationService, never()).cookReceived(any());
    }

    @Test
    void reverseCookAfterOneHourStillMatches() {
        givenLockedUsers(1L, 2L);
        Cook reverse = cook(10L, 2L, 1L, EVENT_NOW.minusHours(1));
        when(cookRepository.findBySenderIdAndReceiverId(2L, 1L)).thenReturn(Optional.of(reverse));
        when(cookRepository.saveAndFlush(any(Cook.class))).thenAnswer(invocation -> {
            Cook saved = invocation.getArgument(0);
            setField(saved, "id", 11L);
            return saved;
        });
        when(matchInfoRepository.saveAndFlush(any(MatchInfo.class))).thenAnswer(invocation -> {
            MatchInfo saved = invocation.getArgument(0);
            setField(saved, "id", 20L);
            return saved;
        });

        var response = cookService.send(1L, new SendCookRequest(2L));

        assertThat(response.matched()).isTrue();
        assertThat(response.matchId()).isEqualTo(20L);
        assertThat(response.status()).isEqualTo("matched");
        assertThat(reverse.getStatus()).isEqualTo(CookStatus.MATCHED);
        assertThat(reverse.getMatchId()).isEqualTo(20L);
        verify(matchInfoRepository).saveAndFlush(any(MatchInfo.class));
    }

    @Test
    void cookLookupKeepsOldPendingCooksInsteadOfExpiringThem() {
        Cook oldSent = cook(10L, 1L, 2L, EVENT_NOW.minusHours(2));
        Cook recentReceived = cook(11L, 3L, 1L, EVENT_NOW.minusMinutes(30));
        when(cookRepository.findAllBySenderIdOrReceiverIdOrderBySentAtDesc(1L, 1L))
                .thenReturn(List.of(recentReceived, oldSent));
        when(profileRepository.findAllById(anyCollection()))
                .thenReturn(List.of(profile(2L, "two"), profile(3L, "three")));
        when(cookRepository.countBySenderIdAndSentAtGreaterThanEqualAndSentAtLessThan(eq(1L), any(), any()))
                .thenReturn(4L);
        when(cookRepository.countBySenderId(1L)).thenReturn(7L);

        var response = cookService.getCooks(1L);

        assertThat(oldSent.getStatus()).isEqualTo(CookStatus.PENDING);
        assertThat(recentReceived.getStatus()).isEqualTo(CookStatus.PENDING);
        assertThat(response.sent()).singleElement().satisfies(item -> {
            assertThat(item.userId()).isEqualTo(2L);
            assertThat(item.profile().nickname()).isEqualTo("two");
            assertThat(item.status()).isEqualTo("pending");
        });
        assertThat(response.received()).singleElement().satisfies(item ->
                assertThat(item.userId()).isEqualTo(3L)
        );
        assertThat(response.usage().todayUsed()).isEqualTo(4L);
        assertThat(response.usage().dailyLimit()).isEqualTo(10);
        assertThat(response.usage().totalUsed()).isEqualTo(7L);
    }

    @Test
    void cancelRejectsMissingCook() {
        when(cookRepository.findById(10L)).thenReturn(Optional.empty());

        assertErrorCode(() -> cookService.cancel(1L, 10L), ErrorCode.NOT_FOUND);
    }

    @Test
    void cancelRejectsNonSender() {
        Cook cook = cook(10L, 1L, 2L, EVENT_NOW.minusMinutes(5));
        when(cookRepository.findById(10L)).thenReturn(Optional.of(cook));

        assertErrorCode(() -> cookService.cancel(2L, 10L), ErrorCode.FORBIDDEN);

        assertThat(cook.getStatus()).isEqualTo(CookStatus.PENDING);
    }

    @Test
    void cancelRejectsAlreadyMatchedCook() {
        Cook cook = cook(10L, 1L, 2L, EVENT_NOW.minusMinutes(5));
        cook.match(20L);
        when(cookRepository.findById(10L)).thenReturn(Optional.of(cook));

        assertErrorCode(() -> cookService.cancel(1L, 10L), ErrorCode.ALREADY_MATCHED);
    }

    @Test
    void cancelRejectsAlreadyExpiredCook() {
        Cook cook = cook(10L, 1L, 2L, EVENT_NOW.minusHours(2));
        setField(cook, "status", CookStatus.EXPIRED);
        when(cookRepository.findById(10L)).thenReturn(Optional.of(cook));

        assertErrorCode(() -> cookService.cancel(1L, 10L), ErrorCode.ALREADY_EXPIRED);

        assertThat(cook.getStatus()).isEqualTo(CookStatus.EXPIRED);
    }

    @Test
    void cancelAllowsOldPendingCookWithoutHourExpiry() {
        Cook cook = cook(10L, 1L, 2L, EVENT_NOW.minusHours(2));
        when(cookRepository.findById(10L)).thenReturn(Optional.of(cook));

        cookService.cancel(1L, 10L);

        assertThat(cook.getStatus()).isEqualTo(CookStatus.CANCELLED);
    }

    @Test
    void cancelMarksPendingCookAsCancelled() {
        Cook cook = cook(10L, 1L, 2L, EVENT_NOW.minusMinutes(5));
        when(cookRepository.findById(10L)).thenReturn(Optional.of(cook));

        cookService.cancel(1L, 10L);

        assertThat(cook.getStatus()).isEqualTo(CookStatus.CANCELLED);
    }

    @Test
    void cancelledCooksAreExcludedFromBothSentAndReceivedLists() {
        Cook cancelledSent = cook(10L, 1L, 2L, EVENT_NOW.minusMinutes(30));
        cancelledSent.cancel();
        Cook activeReceived = cook(11L, 3L, 1L, EVENT_NOW.minusMinutes(10));
        when(cookRepository.findAllBySenderIdOrReceiverIdOrderBySentAtDesc(1L, 1L))
                .thenReturn(List.of(activeReceived, cancelledSent));
        when(profileRepository.findAllById(anyCollection())).thenReturn(List.of(profile(3L, "three")));
        when(cookRepository.countBySenderIdAndSentAtGreaterThanEqualAndSentAtLessThan(eq(1L), any(), any()))
                .thenReturn(0L);
        when(cookRepository.countBySenderId(1L)).thenReturn(1L);

        var response = cookService.getCooks(1L);

        assertThat(response.sent()).isEmpty();
        assertThat(response.received()).singleElement().satisfies(item ->
                assertThat(item.userId()).isEqualTo(3L)
        );
    }

    private void givenLockedUsers(Long firstId, Long secondId) {
        List<Long> ids = List.of(Math.min(firstId, secondId), Math.max(firstId, secondId));
        when(cookUserRepository.findAllByIdForUpdate(ids)).thenReturn(List.of(user(firstId), user(secondId)));
    }

    private static User user(Long id) {
        User user = User.createParticipant("user" + id + "@example.com", EVENT_NOW);
        setField(user, "id", id);
        return user;
    }

    private static Cook cook(Long id, Long senderId, Long receiverId, LocalDateTime sentAt) {
        Cook cook = Cook.pending(senderId, receiverId, sentAt);
        setField(cook, "id", id);
        return cook;
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
