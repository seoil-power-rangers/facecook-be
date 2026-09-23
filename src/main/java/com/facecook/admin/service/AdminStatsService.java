package com.facecook.admin.service;

import com.facecook.admin.config.ActiveUserCriterion;
import com.facecook.admin.config.AdminStatsProperties;
import com.facecook.admin.dto.AdminStatsResponse;
import com.facecook.auth.entity.UserStatus;
import com.facecook.auth.repository.UserRepository;
import com.facecook.cook.repository.CookRepository;
import com.facecook.match.repository.MatchInfoRepository;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.repository.MatchMissionRepository;
import com.facecook.report.entity.ReportStatus;
import com.facecook.report.repository.ReportRepository;
import com.facecook.common.time.EventTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 대시보드용 전체 통계 스냅샷을 만든다.
 *
 * <p>사용자·콕·매칭·미션완료·신고 다섯 도메인의 카운트를 한 응답으로
 * 묶어서 보여주는 게 이 클래스의 유일한 역할이라, 의존성(리포지토리)이
 * 도메인 수만큼 많아 보여도 그 자체가 문제는 아니다 — 각 필드가 정확히
 * 한 값만 채우고 서로 얽히지 않는다({@link #getStats} 참고).</p>
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {
    private final UserRepository userRepository;
    private final CookRepository cookRepository;
    private final MatchInfoRepository matchInfoRepository;
    private final MatchMissionRepository matchMissionRepository;
    private final ReportRepository reportRepository;
    private final AdminStatsProperties properties;
    private final Clock clock;

    /**
     * 전체 유저 수, 오늘 활동한 유저 수, 누적 콕 수, 누적 매칭 수, 미션
     * 완주(STEP 3까지 완료) 매칭 수, 처리 대기 중인 신고 수를 한 번에
     * 반환한다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 없음(읽기 전용). 6개 값 각각 서로 다른 리포지토리에서
     * 독립적으로 집계하는 단순 COUNT라, 값 사이에 순서·일관성 보장은
     * 없다(찰나의 시차로 totalMatches와 missionCleared가 아주 살짝 안
     * 맞을 수 있음 — 대시보드 스냅샷 용도라 문제 없음).</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #countActiveUsers()
     */
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        return new AdminStatsResponse(
                userRepository.count(),
                countActiveUsers(),
                cookRepository.count(),
                matchInfoRepository.count(),
                matchMissionRepository.countByCurrentStepGreaterThanEqual(MatchMission.COMPLETED_STEP),
                reportRepository.countByStatus(ReportStatus.PENDING)
        );
    }

    /**
     * 이 "활동 중" 기준은 참가자용 GET /api/stats(ProfileActivityLookup, 15분
     * 롤링 기준)와 일부러 다르다. 여긴 하루 단위 운영 리포트용이라 자정
     * 기준으로 하루치를 세고, 참가자 화면은 "지금 접속해 있나"를 실시간에
     * 가깝게 보여줘야 해서 짧은 롤링 창을 쓴다. 두 수치가 다르게 보여도
     * 버그가 아니라 의도된 차이다 — 하나로 통일하지 않는다.
     */
    private long countActiveUsers() {
        if (properties.activeUserCriterion() == ActiveUserCriterion.STATUS) {
            return userRepository.countByStatus(UserStatus.ACTIVE);
        }

        LocalDate eventDate = EventTime.today(clock);
        LocalDateTime startInclusive = eventDate.atStartOfDay();
        return userRepository.countByLastActiveAtGreaterThanEqualAndLastActiveAtLessThan(
                startInclusive,
                startInclusive.plusDays(1)
        );
    }
}
