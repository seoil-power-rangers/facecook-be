package com.facecook.report.service;

import com.facecook.auth.entity.User;
import com.facecook.auth.repository.UserRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.report.dto.CreateReportRequest;
import com.facecook.report.dto.ReportChatMessageResponse;
import com.facecook.report.dto.ReportResponse;
import com.facecook.report.dto.ResolveReportRequest;
import com.facecook.report.entity.Report;
import com.facecook.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {
    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest request) {
        if (reporterId.equals(request.reportedUserId())) {
            throw new ApiException(ErrorCode.VALIDATION, "자기 자신을 신고할 수 없습니다.");
        }
        if (!userRepository.existsById(request.reportedUserId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "신고 대상 사용자를 찾을 수 없습니다.");
        }

        Report report = Report.create(
                reporterId,
                request.reportedUserId(),
                request.reason(),
                request.detail(),
                now()
        );
        return ReportResponse.from(reportRepository.save(report));
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> getAll() {
        return reportRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse get(Long reportId) {
        return ReportResponse.from(findReport(reportId));
    }

    @Transactional
    public ReportResponse resolve(Long reportId, Long adminId, ResolveReportRequest request) {
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
        report.review(adminId, now());

        if (request.suspend()) {
            User reportedUser = userRepository.findById(report.getReportedUserId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "신고 대상 사용자를 찾을 수 없습니다."));
            reportedUser.suspend();
        }
        return ReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public List<ReportChatMessageResponse> getChat(Long reportId) {
        Report report = findReport(reportId);
        return reportRepository.findChatMessages(report.getReporterId(), report.getReportedUserId()).stream()
                .map(ReportChatMessageResponse::from)
                .toList();
    }

    private Report findReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), EVENT_ZONE);
    }
}
