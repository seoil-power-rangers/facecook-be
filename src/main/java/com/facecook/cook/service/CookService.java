package com.facecook.cook.service;

import com.facecook.chat.repository.MessageRepository;
import com.facecook.chat.repository.UnreadCountProjection;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.dto.CookItemResponse;
import com.facecook.cook.dto.CookListResponse;
import com.facecook.cook.dto.CookUsageResponse;
import com.facecook.match.dto.MatchResponse;
import com.facecook.match.dto.RecentMessageResponse;
import com.facecook.cook.dto.SendCookRequest;
import com.facecook.cook.dto.SendCookResponse;
import com.facecook.cook.entity.Cook;
import com.facecook.cook.entity.CookStatus;
import com.facecook.match.entity.MatchInfo;
import com.facecook.cook.repository.CookRepository;
import com.facecook.cook.repository.CookUserRepository;
import com.facecook.match.repository.MatchInfoRepository;
import com.facecook.match.repository.RecentMessageProjection;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
import com.facecook.profile.service.ProfileActivityLookup;
import com.facecook.push.service.ParticipantPushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 콕(호감 표시)과 매칭의 유일한 진입점.
 *
 * <p>콕 전송·취소·조회, 매칭 성사·조회, 대화 읽음 처리까지 이 클래스 하나가
 * 담당한다. 매칭 성사는 이 클래스 안에서만 일어난다({@link #send}가 상호
 * 콕을 감지했을 때) — 다른 클래스가 {@code MatchInfo}를 직접 생성하지
 * 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class CookService {
    public static final int DAILY_LIMIT = 10;
    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 그날 전체 참가자가 보낼 수 있는 콕 총량(개인별 하루 10개와는 별개인
     * 시스템 전체 안전판). 예상 활성 참가자 수(300/600/900) × 유저당 하루
     * 한도(10)로 산정했고, 날짜마다 새로 리셋된다(누적 아님). 행사 기간
     * 외 날짜는 이 제한을 적용하지 않는다(로컬/개발 환경 대비).
     */
    private static final Map<LocalDate, Long> EVENT_WIDE_DAILY_LIMITS = Map.of(
            LocalDate.of(2026, 9, 30), 3_000L,
            LocalDate.of(2026, 10, 1), 6_000L,
            LocalDate.of(2026, 10, 2), 9_000L
    );

    private final CookRepository cookRepository;
    private final MatchInfoRepository matchInfoRepository;
    private final CookUserRepository cookUserRepository;
    private final ProfileRepository profileRepository;
    private final ProfileActivityLookup activityLookup;
    private final MessageRepository messageRepository;
    private final ParticipantPushNotificationService pushNotificationService;
    private final Clock clock;

    /**
     * senderId가 request.receiverId()에게 콕을 보낸다. 상대가 이미 나에게
     * pending 콕을 보내둔 상태였다면, 이 호출 안에서 바로 매칭이 성사된다
     * (내가 따로 "수락" API를 부를 필요가 없다 — 맞콕이면 즉시 매칭).
     *
     * <p>전제조건: senderId != receiverId, 두 유저 모두 실존, 서로 매칭된 적
     * 없음, 아직 같은 상대에게 보낸 콕이 없음(사람당 1콕), 오늘 개인
     * 한도({@value #DAILY_LIMIT}개) 이내, 행사 전체 하루 한도
     * ({@link #EVENT_WIDE_DAILY_LIMITS}) 이내.</p>
     *
     * <p>부작용: {@code Cook} 행을 저장한다. 맞콕이면 {@code MatchInfo}를
     * 새로 만들고 양쪽 콕을 matched로 바꾼 뒤 두 사람 모두에게
     * matchCreated 푸시를 보낸다(스스로 만든 매칭이라 별도 알림 이벤트
     * 없음). 맞콕이 아니면 받는 사람에게 cookReceived 푸시만 보낸다.</p>
     *
     * <p>예외: {@code SELF}(자기 자신), {@code NOT_FOUND}(상대 없음),
     * {@code ALREADY_MATCHED}, {@code DUPLICATE}(이미 보낸 콕 있음 — DB
     * unique 제약 위반도 이 코드로 변환됨), {@code DAILY_LIMIT},
     * {@code EVENT_LIMIT}(행사 지정일에만 적용).</p>
     *
     * @see #cancel(Long, Long)
     * @see #createMatch(Cook, Cook, LocalDateTime)
     */
    @Transactional
    public SendCookResponse send(Long senderId, SendCookRequest request) {
        Long receiverId = request.receiverId();
        if (senderId.equals(receiverId)) {
            throw new ApiException(ErrorCode.SELF);
        }

        lockUsersAndValidateReceiver(senderId, receiverId);
        if (matchInfoRepository.existsBetween(senderId, receiverId)) {
            throw new ApiException(ErrorCode.ALREADY_MATCHED);
        }
        if (cookRepository.existsBySenderIdAndReceiverId(senderId, receiverId)) {
            throw new ApiException(ErrorCode.DUPLICATE);
        }

        LocalDateTime now = now();
        DateRange today = today(now.toLocalDate());
        long todayUsed = cookRepository.countBySenderIdAndSentAtGreaterThanEqualAndSentAtLessThan(
                senderId,
                today.startInclusive(),
                today.endExclusive()
        );
        if (todayUsed >= DAILY_LIMIT) {
            throw new ApiException(ErrorCode.DAILY_LIMIT);
        }
        enforceEventWideDailyLimit(now.toLocalDate(), today);

        Optional<Cook> reverseCook = cookRepository.findBySenderIdAndReceiverId(receiverId, senderId);
        reverseCook.ifPresent(cook -> cook.expireIfOverdue(now));

        Cook cook;
        try {
            cook = cookRepository.saveAndFlush(Cook.pending(senderId, receiverId, now));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.DUPLICATE, exception);
        }

        Optional<Cook> pendingReverseCook = reverseCook.filter(Cook::isPending);
        if (pendingReverseCook.isPresent()) {
            createMatch(cook, pendingReverseCook.get(), now);
        } else {
            pushNotificationService.cookReceived(receiverId);
        }
        return SendCookResponse.from(cook);
    }

    /**
     * userId가 자신이 보낸 콕(cookId)을 취소한다.
     *
     * <p>전제조건: cookId 존재, userId가 그 콕의 sender, 아직 매칭·만료 상태가
     * 아님. 만료 여부는 저장된 값이 아니라 이 호출 시점에 실시간으로 판정한다
     * (아래 부작용 참고).</p>
     *
     * <p>부작용: 상태를 CANCELLED로 바꾼다. 또한 호출 시점 기준으로 만료
     * 기한이 지난 콕이면 이 메서드가 먼저 EXPIRED로 갱신해버려서 취소가
     * {@code ALREADY_EXPIRED}로 실패한다 — "취소하려던 콕이 방금 막
     * 만료됨" 케이스를 이렇게 잡아낸다.</p>
     *
     * <p>예외: {@code NOT_FOUND}, {@code FORBIDDEN}(본인 콕 아님),
     * {@code ALREADY_MATCHED}, {@code ALREADY_EXPIRED}.</p>
     *
     * @see #send(Long, SendCookRequest)
     */
    @Transactional
    public void cancel(Long userId, Long cookId) {
        Cook cook = cookRepository.findById(cookId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!cook.getSenderId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }

        cook.expireIfOverdue(now());
        if (cook.getStatus() == CookStatus.MATCHED) {
            throw new ApiException(ErrorCode.ALREADY_MATCHED);
        }
        if (cook.getStatus() == CookStatus.EXPIRED) {
            throw new ApiException(ErrorCode.ALREADY_EXPIRED);
        }
        cook.cancel();
    }

    /**
     * userId가 보낸/받은 콕 목록(취소된 콕 제외)과 오늘·누적 사용량을 함께
     * 반환한다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 겉보기엔 조회 전용이지만, 목록에 포함된 콕 중 만료 기한이
     * 지난 pending 콕이 있으면 이 호출 안에서 즉시 EXPIRED로 갱신하고 그
     * 결과를 응답에 반영한다({@code @Transactional}이지 readOnly가 아닌
     * 이유) — 그래서 "만료됐는데 목록엔 아직 pending으로 보이는" 상태가
     * 생기지 않는다.</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #getMatches(Long)
     * @see #send(Long, SendCookRequest)
     */
    @Transactional
    public CookListResponse getCooks(Long userId) {
        LocalDateTime now = now();
        List<Cook> cooks = cookRepository.findAllBySenderIdOrReceiverIdOrderBySentAtDesc(userId, userId)
                .stream()
                .filter(cook -> cook.getStatus() != CookStatus.CANCELLED)
                .toList();
        cooks.forEach(cook -> cook.expireIfOverdue(now));

        Map<Long, ProfileResponse> profiles = profileResponses(
                cooks.stream().map(cook -> cook.otherUserId(userId)).collect(Collectors.toSet())
        );
        List<CookItemResponse> sent = cooks.stream()
                .filter(cook -> cook.getSenderId().equals(userId))
                .map(cook -> toCookItem(cook, userId, profiles))
                .toList();
        List<CookItemResponse> received = cooks.stream()
                .filter(cook -> cook.getReceiverId().equals(userId))
                .map(cook -> toCookItem(cook, userId, profiles))
                .toList();

        DateRange today = today(now.toLocalDate());
        long todayUsed = cookRepository.countBySenderIdAndSentAtGreaterThanEqualAndSentAtLessThan(
                userId,
                today.startInclusive(),
                today.endExclusive()
        );
        long totalUsed = cookRepository.countBySenderId(userId);
        return new CookListResponse(
                sent,
                received,
                new CookUsageResponse(todayUsed, DAILY_LIMIT, totalUsed)
        );
    }

    /**
     * userId의 매칭 목록을 성사 시각 최신순으로 반환한다. 매칭마다 상대
     * 프로필·최근 메시지·안읽음 개수를 같이 채운다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 없음(읽기 전용 트랜잭션). 상대 프로필·최근 메시지·안읽음
     * 개수는 매칭 개수와 무관하게 각각 쿼리 1번으로 배치 조회한다 — 매칭당
     * 개별 조회하면 N+1이 난다(부하테스트로 실측 확인, PR #67).</p>
     *
     * <p>예외: {@code PROFILE_NOT_FOUND}(상대 프로필이 없는 비정상
     * 상황 — 정상 흐름에서는 발생하지 않는다).</p>
     *
     * @see #getMatch(Long, Long)
     * @see #markRead(Long, Long)
     */
    @Transactional(readOnly = true)
    public List<MatchResponse> getMatches(Long userId) {
        List<MatchInfo> matches = matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(userId, userId);
        // 매칭마다 상대 프로필·최근 메시지·안읽음 개수를 따로 조회하면 N+1이 난다
        // — 셋 다 한 번에 배치 조회한다.
        Map<Long, ProfileResponse> partnerProfiles = profileResponses(
                matches.stream().map(matchInfo -> matchInfo.otherUserId(userId)).toList()
        );
        Map<Long, RecentMessageResponse> recentMessages = recentMessagesByMatchId(matchIds(matches));
        Map<Long, Long> unreadCounts = unreadCountsByMatchId(matchIds(matches), userId);
        return matches.stream()
                .map(matchInfo -> toMatchResponse(matchInfo, userId, partnerProfiles, recentMessages, unreadCounts))
                .toList();
    }

    /**
     * 매칭 하나의 상세(상대 프로필, 최근 메시지, 안읽음 개수)를 반환한다.
     *
     * <p>전제조건: matchId 존재, userId가 그 매칭의 당사자(userA 또는
     * userB).</p>
     *
     * <p>부작용: 없음(읽기 전용 트랜잭션).</p>
     *
     * <p>예외: {@code NOT_FOUND}(매칭 없음), {@code FORBIDDEN}(당사자
     * 아님), {@code PROFILE_NOT_FOUND}.</p>
     *
     * @see #getMatches(Long)
     */
    @Transactional(readOnly = true)
    public MatchResponse getMatch(Long userId, Long matchId) {
        MatchInfo matchInfo = matchInfoRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        if (!matchInfo.includes(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        Long partnerId = matchInfo.otherUserId(userId);
        List<Long> singleMatchId = List.of(matchInfo.getId());
        return toMatchResponse(
                matchInfo,
                userId,
                profileResponses(List.of(partnerId)),
                recentMessagesByMatchId(singleMatchId),
                unreadCountsByMatchId(singleMatchId, userId)
        );
    }

    private void enforceEventWideDailyLimit(LocalDate today, DateRange range) {
        Long limit = EVENT_WIDE_DAILY_LIMITS.get(today);
        if (limit == null) {
            return;
        }
        long sentToday = cookRepository.countBySentAtGreaterThanEqualAndSentAtLessThan(
                range.startInclusive(),
                range.endExclusive()
        );
        if (sentToday >= limit) {
            throw new ApiException(ErrorCode.EVENT_LIMIT);
        }
    }

    private void lockUsersAndValidateReceiver(Long senderId, Long receiverId) {
        List<Long> userIds = List.of(Math.min(senderId, receiverId), Math.max(senderId, receiverId));
        boolean receiverExists = cookUserRepository.findAllByIdForUpdate(userIds).stream()
                .anyMatch(user -> user.getId().equals(receiverId));
        if (!receiverExists) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    private void createMatch(Cook cook, Cook reverseCook, LocalDateTime matchedAt) {
        MatchInfo matchInfo = matchInfoRepository.saveAndFlush(
                MatchInfo.create(cook.getSenderId(), cook.getReceiverId(), matchedAt)
        );
        cook.match(matchInfo.getId());
        reverseCook.match(matchInfo.getId());
        pushNotificationService.matchCreated(matchInfo.getUserAId(), matchInfo.getId());
        pushNotificationService.matchCreated(matchInfo.getUserBId(), matchInfo.getId());
    }

    private CookItemResponse toCookItem(
            Cook cook,
            Long userId,
            Map<Long, ProfileResponse> profiles
    ) {
        Long otherUserId = cook.otherUserId(userId);
        return CookItemResponse.from(cook, userId, profiles.get(otherUserId));
    }

    private MatchResponse toMatchResponse(
            MatchInfo matchInfo,
            Long userId,
            Map<Long, ProfileResponse> partnerProfiles,
            Map<Long, RecentMessageResponse> recentMessages,
            Map<Long, Long> unreadCounts
    ) {
        Long partnerId = matchInfo.otherUserId(userId);
        ProfileResponse partner = Optional.ofNullable(partnerProfiles.get(partnerId))
                .orElseThrow(() -> new ApiException(ErrorCode.PROFILE_NOT_FOUND));
        return MatchResponse.from(
                matchInfo,
                partner,
                recentMessages.get(matchInfo.getId()),
                unreadCounts.getOrDefault(matchInfo.getId(), 0L)
        );
    }

    private List<Long> matchIds(List<MatchInfo> matches) {
        return matches.stream().map(MatchInfo::getId).toList();
    }

    private Map<Long, RecentMessageResponse> recentMessagesByMatchId(List<Long> matchIds) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        return matchInfoRepository.findRecentMessagesByMatchIds(matchIds).stream()
                .collect(Collectors.toMap(RecentMessageProjection::getMatchId, RecentMessageResponse::from));
    }

    /**
     * 매칭마다 안읽음 개수를 DB에서 직접 집계해온다(매칭당 쿼리 1개였던 걸
     * 매칭 개수와 무관하게 쿼리 1개로 줄인다). lastReadAt 비교까지 쿼리 안에서
     * 끝내기 때문에, 메시지가 아주 많이 쌓인 매칭이어도 개수만 돌아온다.
     */
    private Map<Long, Long> unreadCountsByMatchId(List<Long> matchIds, Long userId) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.findUnreadCountsByMatchIds(matchIds, userId).stream()
                .collect(Collectors.toMap(UnreadCountProjection::getMatchId, UnreadCountProjection::getUnreadCount));
    }

    /**
     * userId 기준으로 matchId 대화방을 "지금 이 시각까지 읽었다"고
     * 기록한다. {@link #getMatches}/{@link #getMatch}의 unreadCount는 이
     * 값을 기준으로 계산된다.
     *
     * <p>전제조건: matchId 존재, userId가 그 매칭의 당사자.</p>
     *
     * <p>부작용: {@code MatchInfo}에서 userId 쪽 lastReadAt을 현재
     * 시각으로 갱신한다(상대 쪽 값은 안 건드림 — 두 사람의 읽음 시각은
     * 서로 독립적이다).</p>
     *
     * <p>예외: {@code NOT_FOUND}, {@code FORBIDDEN}.</p>
     *
     * @see #getMatches(Long)
     * @see #getMatch(Long, Long)
     */
    @Transactional
    public void markRead(Long userId, Long matchId) {
        MatchInfo matchInfo = matchInfoRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        if (!matchInfo.includes(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        matchInfo.markRead(userId, now());
    }

    private Map<Long, ProfileResponse> profileResponses(Collection<Long> userIds) {
        return activityLookup.toResponses(profileRepository.findAllById(userIds)).stream()
                .collect(Collectors.toMap(ProfileResponse::userId, Function.identity()));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), EVENT_ZONE);
    }

    private DateRange today(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        return new DateRange(start, start.plusDays(1));
    }

    private record DateRange(LocalDateTime startInclusive, LocalDateTime endExclusive) {
    }
}
