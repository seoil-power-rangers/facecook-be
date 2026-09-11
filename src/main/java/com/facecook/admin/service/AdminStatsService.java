package com.facecook.admin.service;

import com.facecook.admin.config.ActiveUserCriterion;
import com.facecook.admin.config.AdminStatsProperties;
import com.facecook.admin.dto.AdminStatsResponse;
import com.facecook.auth.entity.UserStatus;
import com.facecook.auth.repository.UserRepository;
import com.facecook.cook.repository.CookRepository;
import com.facecook.cook.repository.MatchInfoRepository;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.repository.MatchMissionRepository;
import com.facecook.report.entity.ReportStatus;
import com.facecook.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AdminStatsService {
    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final CookRepository cookRepository;
    private final MatchInfoRepository matchInfoRepository;
    private final MatchMissionRepository matchMissionRepository;
    private final ReportRepository reportRepository;
    private final AdminStatsProperties properties;
    private final Clock clock;

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

    private long countActiveUsers() {
        if (properties.activeUserCriterion() == ActiveUserCriterion.STATUS) {
            return userRepository.countByStatus(UserStatus.ACTIVE);
        }

        LocalDate eventDate = LocalDate.now(clock.withZone(EVENT_ZONE));
        LocalDateTime startInclusive = eventDate.atStartOfDay();
        return userRepository.countByLastActiveAtGreaterThanEqualAndLastActiveAtLessThan(
                startInclusive,
                startInclusive.plusDays(1)
        );
    }
}
