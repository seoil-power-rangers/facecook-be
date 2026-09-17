package com.facecook.cook.service;

import com.facecook.chat.repository.MessageRepository;
import com.facecook.chat.repository.UnreadMessageProjection;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.dto.CookItemResponse;
import com.facecook.cook.dto.CookListResponse;
import com.facecook.cook.dto.CookUsageResponse;
import com.facecook.cook.dto.MatchResponse;
import com.facecook.cook.dto.RecentMessageResponse;
import com.facecook.cook.dto.SendCookRequest;
import com.facecook.cook.dto.SendCookResponse;
import com.facecook.cook.entity.Cook;
import com.facecook.cook.entity.CookStatus;
import com.facecook.cook.entity.MatchInfo;
import com.facecook.cook.repository.CookRepository;
import com.facecook.cook.repository.CookUserRepository;
import com.facecook.cook.repository.MatchInfoRepository;
import com.facecook.cook.repository.RecentMessageProjection;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public List<MatchResponse> getMatches(Long userId) {
        List<MatchInfo> matches = matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(userId, userId);
        // 매칭마다 상대 프로필·최근 메시지·안읽음 개수를 따로 조회하면 N+1이 난다
        // — 셋 다 한 번에 배치 조회한다.
        Map<Long, ProfileResponse> partnerProfiles = profileResponses(
                matches.stream().map(matchInfo -> matchInfo.otherUserId(userId)).toList()
        );
        Map<Long, RecentMessageResponse> recentMessages = recentMessagesByMatchId(matchIds(matches));
        Map<Long, Long> unreadCounts = unreadCountsByMatchId(matches, userId);
        return matches.stream()
                .map(matchInfo -> toMatchResponse(matchInfo, userId, partnerProfiles, recentMessages, unreadCounts))
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Long userId, Long matchId) {
        MatchInfo matchInfo = matchInfoRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        if (!matchInfo.includes(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        Long partnerId = matchInfo.otherUserId(userId);
        List<MatchInfo> singleMatch = List.of(matchInfo);
        return toMatchResponse(
                matchInfo,
                userId,
                profileResponses(List.of(partnerId)),
                recentMessagesByMatchId(matchIds(singleMatch)),
                unreadCountsByMatchId(singleMatch, userId)
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
     * 매칭마다 lastReadAt 기준이 달라서 쿼리 하나로 필터링까지 표현하기
     * 어렵다 — 관련 메시지 시각만 한 번에 가져오고, 매칭별 기준 시각 비교는
     * 메모리에서 처리한다(매칭당 쿼리 2개였던 걸 쿼리 1개로 줄인다).
     */
    private Map<Long, Long> unreadCountsByMatchId(List<MatchInfo> matches, Long userId) {
        if (matches.isEmpty()) {
            return Map.of();
        }
        List<Long> matchIds = matchIds(matches);
        // Collectors.toMap은 값이 null이면 내부 Map.merge에서 NPE를 던진다 —
        // 안 읽은 매칭은 lastReadAt이 null이라 직접 채운다.
        Map<Long, LocalDateTime> lastReadAtByMatchId = new HashMap<>();
        for (MatchInfo matchInfo : matches) {
            lastReadAtByMatchId.put(matchInfo.getId(), matchInfo.lastReadAt(userId));
        }
        Map<Long, List<LocalDateTime>> sentAtByMatchId = messageRepository
                .findSentAtForUnreadCount(matchIds, userId).stream()
                .collect(Collectors.groupingBy(
                        UnreadMessageProjection::getMatchId,
                        Collectors.mapping(UnreadMessageProjection::getSentAt, Collectors.toList())
                ));

        Map<Long, Long> counts = new HashMap<>();
        for (Long matchId : matchIds) {
            LocalDateTime lastReadAt = lastReadAtByMatchId.get(matchId);
            List<LocalDateTime> sentTimes = sentAtByMatchId.getOrDefault(matchId, List.of());
            long count = lastReadAt == null
                    ? sentTimes.size()
                    : sentTimes.stream().filter(sentAt -> sentAt.isAfter(lastReadAt)).count();
            counts.put(matchId, count);
        }
        return counts;
    }

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
