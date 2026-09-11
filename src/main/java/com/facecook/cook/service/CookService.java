package com.facecook.cook.service;

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
import com.facecook.cook.entity.MatchInfo;
import com.facecook.cook.repository.CookRepository;
import com.facecook.cook.repository.CookUserRepository;
import com.facecook.cook.repository.MatchInfoRepository;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.entity.Profile;
import com.facecook.profile.repository.ProfileRepository;
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

@Service
@RequiredArgsConstructor
public class CookService {
    public static final int DAILY_LIMIT = 10;
    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    private final CookRepository cookRepository;
    private final MatchInfoRepository matchInfoRepository;
    private final CookUserRepository cookUserRepository;
    private final ProfileRepository profileRepository;
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

        Optional<Cook> reverseCook = cookRepository.findBySenderIdAndReceiverId(receiverId, senderId);
        reverseCook.ifPresent(cook -> cook.expireIfOverdue(now));

        Cook cook;
        try {
            cook = cookRepository.saveAndFlush(Cook.pending(senderId, receiverId, now));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.DUPLICATE, exception);
        }

        reverseCook.filter(Cook::isPending).ifPresent(reverse -> createMatch(cook, reverse, now));
        return SendCookResponse.from(cook);
    }

    @Transactional
    public CookListResponse getCooks(Long userId) {
        LocalDateTime now = now();
        List<Cook> cooks = cookRepository.findAllBySenderIdOrReceiverIdOrderBySentAtDesc(userId, userId);
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
        return matchInfoRepository.findAllByUserAIdOrUserBIdOrderByMatchedAtDesc(userId, userId).stream()
                .map(matchInfo -> toMatchResponse(matchInfo, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(Long userId, Long matchId) {
        MatchInfo matchInfo = matchInfoRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));
        if (!matchInfo.includes(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return toMatchResponse(matchInfo, userId);
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
    }

    private CookItemResponse toCookItem(
            Cook cook,
            Long userId,
            Map<Long, ProfileResponse> profiles
    ) {
        Long otherUserId = cook.otherUserId(userId);
        return CookItemResponse.from(cook, userId, profiles.get(otherUserId));
    }

    private MatchResponse toMatchResponse(MatchInfo matchInfo, Long userId) {
        Long partnerId = matchInfo.otherUserId(userId);
        ProfileResponse partner = profileRepository.findById(partnerId)
                .map(ProfileResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.PROFILE_NOT_FOUND));
        RecentMessageResponse recentMessage = matchInfoRepository.findRecentMessage(matchInfo.getId())
                .map(RecentMessageResponse::from)
                .orElse(null);
        return MatchResponse.from(matchInfo, partner, recentMessage);
    }

    private Map<Long, ProfileResponse> profileResponses(Collection<Long> userIds) {
        return profileRepository.findAllById(userIds).stream()
                .map(ProfileResponse::from)
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
