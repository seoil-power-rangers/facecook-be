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
import com.facecook.common.time.EventTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 신고 접수·조회·처리(관리자).
 *
 * <p>신고 처리({@link #resolve})는 필요하면 신고당한 유저를 그 자리에서
 * 정지시킨다({@code User.suspend()}) — 신고 도메인이 유저 도메인에 직접
 * 쓰기를 하는 유일한 지점이다.</p>
 */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * reporterId가 request.reportedUserId()를 신고한다(사유·상세 내용
     * 포함, 상태는 PENDING으로 시작).
     *
     * <p>전제조건: reporterId != reportedUserId, 신고 대상 유저 존재.</p>
     *
     * <p>부작용: {@code Report} 행을 저장한다.</p>
     *
     * <p>예외: {@code VALIDATION}(자기 자신 신고), {@code NOT_FOUND}
     * (대상 없음).</p>
     *
     * @see #resolve(Long, Long, ResolveReportRequest)
     */
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
                EventTime.now(clock)
        );
        return ReportResponse.from(reportRepository.save(report));
    }

    /**
     * 전체 신고 목록을 최신 접수순(생성시각·id 내림차순)으로 반환한다
     * (관리자 화면용).
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 없음. 페이지네이션 없이 전체를 반환한다.</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #get(Long)
     */
    @Transactional(readOnly = true)
    public List<ReportResponse> getAll() {
        return reportRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(ReportResponse::from)
                .toList();
    }

    /**
     * 신고 하나의 상세를 반환한다.
     *
     * <p>전제조건: reportId 존재.</p>
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외: {@code REPORT_NOT_FOUND}.</p>
     *
     * @see #getChat(Long)
     */
    @Transactional(readOnly = true)
    public ReportResponse get(Long reportId) {
        return ReportResponse.from(findReport(reportId));
    }

    /**
     * adminId(관리자)가 신고를 검토 완료 처리한다. request.suspend()가
     * true면 신고당한 유저 계정도 같이 정지시킨다.
     *
     * <p>전제조건: reportId 존재, 아직 REVIEWED 상태가 아님(이미 처리된
     * 신고는 다시 처리 못 함). suspend가 true면 신고 대상 유저가 실존해야
     * 함.</p>
     *
     * <p>부작용: {@code Report} 행을 잠근 뒤(동시 처리 방지) 상태를
     * REVIEWED로 바꾸고 처리자·처리시각을 기록한다. suspend가 true면
     * 대상 유저의 상태도 정지로 바꾼다 — 신고 처리 하나로 두 엔티티가
     * 같이 바뀌는 유일한 지점.</p>
     *
     * <p>예외: {@code REPORT_NOT_FOUND}, {@code REPORT_ALREADY_REVIEWED}
     * ({@code Report#review} 내부에서 던짐), {@code NOT_FOUND}(suspend
     * 대상 유저가 이미 없어진 경우).</p>
     *
     * @see #create(Long, CreateReportRequest)
     */
    @Transactional
    public ReportResponse resolve(Long reportId, Long adminId, ResolveReportRequest request) {
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
        report.review(adminId, EventTime.now(clock));

        if (request.suspend()) {
            User reportedUser = userRepository.findById(report.getReportedUserId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "신고 대상 사용자를 찾을 수 없습니다."));
            reportedUser.suspend();
        }
        return ReportResponse.from(report);
    }

    /**
     * 신고자와 신고 대상 사이의 채팅 내역을 시간순으로 반환한다(관리자가
     * 신고 사실을 판단할 증거 자료).
     *
     * <p>전제조건: reportId 존재.</p>
     *
     * <p>부작용: 없음. 두 사람이 실제로 매칭된 적 없으면(대화 자체가
     * 없으면) 빈 목록을 반환한다 — 별도 에러 아님.</p>
     *
     * <p>예외: {@code REPORT_NOT_FOUND}.</p>
     *
     * @see #get(Long)
     */
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

}
