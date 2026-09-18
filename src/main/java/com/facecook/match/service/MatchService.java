package com.facecook.match.service;

import com.facecook.chat.repository.MessageRepository;
import com.facecook.chat.repository.UnreadCountProjection;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.match.dto.MatchResponse;
import com.facecook.match.dto.RecentMessageResponse;
import com.facecook.match.entity.MatchInfo;
import com.facecook.match.repository.MatchInfoRepository;
import com.facecook.match.repository.RecentMessageProjection;
import com.facecook.profile.dto.ProfileResponse;
import com.facecook.profile.repository.ProfileRepository;
import com.facecook.profile.service.ProfileActivityLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 매칭 조회·읽음 처리의 유일한 진입점.
 *
 * <p>매칭 성사(생성) 자체는 이 클래스가 아니라 {@link
 * com.facecook.cook.service.CookService#send}에서 일어난다 — 맞콕을
 * 감지했을 때 콕 도메인이 {@code MatchInfo}를 직접 만든다. 이 클래스는
 * 이미 성사된 매칭을 조회하고 읽음 상태를 관리하는 쪽만 담당한다.</p>
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    private final MatchInfoRepository matchInfoRepository;
    private final ProfileRepository profileRepository;
    private final ProfileActivityLookup activityLookup;
    private final MessageRepository messageRepository;
    private final Clock clock;

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

    private Map<Long, ProfileResponse> profileResponses(Collection<Long> userIds) {
        return activityLookup.toResponses(profileRepository.findAllById(userIds)).stream()
                .collect(Collectors.toMap(ProfileResponse::userId, Function.identity()));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), EVENT_ZONE);
    }
}
