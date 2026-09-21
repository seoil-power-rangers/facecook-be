package com.facecook.cook.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.dto.CookItemResponse;
import com.facecook.cook.dto.CookListResponse;
import com.facecook.cook.dto.CookUsageResponse;
import com.facecook.cook.dto.SendCookRequest;
import com.facecook.cook.dto.SendCookResponse;
import com.facecook.cook.entity.Cook;
import com.facecook.cook.entity.CookStatus;
import com.facecook.match.entity.MatchInfo;
import com.facecook.cook.repository.CookRepository;
import com.facecook.cook.repository.CookUserRepository;
import com.facecook.match.repository.MatchInfoRepository;
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
 * 콕(호감 표시)의 유일한 진입점이자, 매칭이 "성사"되는 유일한 지점.
 *
 * <p>콕 전송·취소·조회를 담당한다. 매칭 성사는 이 클래스 안에서만
 * 일어난다({@link #send}가 상호 콕을 감지했을 때) — 다른 클래스가
 * {@code MatchInfo}를 직접 생성하지 않는다. 성사된 매칭의 조회·읽음
 * 처리는 {@link com.facecook.match.service.MatchService}가 담당한다.</p>
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
     * <p>전제조건: cookId 존재, userId가 그 콕의 sender, 아직 매칭·만료 상태가 아님. 이미 CANCELLED인
     * 콕을 다시 취소하면 상태를 바꾸지 않고 성공한다.</p>
     *
     * <p>부작용: 상태를 CANCELLED로 바꾼다(저장은 트랜잭션 커밋 시 더티 체킹).</p>
     *
     * <p>예외: {@code NOT_FOUND}, {@code FORBIDDEN}(본인 콕 아님), {@code ALREADY_MATCHED},
     * {@code ALREADY_EXPIRED}(레거시 EXPIRED 행). 검사 순서는 {@link Cook#cancel(Long)}이 정한다.</p>
     *
     * @see #send(Long, SendCookRequest)
     * @see Cook#cancel(Long)
     */
    @Transactional
    public void cancel(Long userId, Long cookId) {
        Cook cook = cookRepository.findById(cookId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        cook.cancel(userId);
    }

    /**
     * userId가 보낸/받은 콕 목록(취소된 콕 제외)과 오늘·누적 사용량을 함께 반환한다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 없다. 조회 전용 트랜잭션이다.</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #send(Long, SendCookRequest)
     */
    @Transactional(readOnly = true)
    public CookListResponse getCooks(Long userId) {
        LocalDateTime now = now();
        List<Cook> cooks = cookRepository.findAllBySenderIdOrReceiverIdOrderBySentAtDesc(userId, userId)
                .stream()
                .filter(cook -> cook.getStatus() != CookStatus.CANCELLED)
                .toList();

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
