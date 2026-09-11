package com.facecook.report.service;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserStatus;
import com.facecook.auth.repository.UserRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.report.dto.CreateReportRequest;
import com.facecook.report.dto.ResolveReportRequest;
import com.facecook.report.entity.Report;
import com.facecook.report.entity.ReportStatus;
import com.facecook.report.repository.ReportChatMessageProjection;
import com.facecook.report.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");
    private static final LocalDateTime EVENT_NOW = LocalDateTime.of(2026, 9, 30, 12, 0);

    private ReportRepository reportRepository;
    private UserRepository userRepository;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        userRepository = mock(UserRepository.class);
        reportService = new ReportService(
                reportRepository,
                userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsPendingReport() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            setField(report, "id", 10L);
            return report;
        });

        var response = reportService.create(1L, new CreateReportRequest(2L, "욕설", "채팅에서 욕설"));

        assertThat(response.reportId()).isEqualTo(10L);
        assertThat(response.reporterId()).isEqualTo(1L);
        assertThat(response.reportedUserId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.createdAt()).isEqualTo(EVENT_NOW);
    }

    @Test
    void rejectsSelfReport() {
        assertThatThrownBy(() -> reportService.create(1L, new CreateReportRequest(1L, "사유", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
    }

    @Test
    void rejectsMissingReportedUser() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> reportService.create(1L, new CreateReportRequest(99L, "사유", null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void resolvesReportWithoutSuspendingUser() {
        Report report = report(10L, 1L, 2L);
        when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));

        var response = reportService.resolve(10L, 7L, new ResolveReportRequest(false));

        assertThat(response.status()).isEqualTo("reviewed");
        assertThat(response.reviewedBy()).isEqualTo(7L);
        assertThat(response.reviewedAt()).isEqualTo(EVENT_NOW);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.REVIEWED);
    }

    @Test
    void resolvesReportAndSuspendsReportedUser() {
        Report report = report(10L, 1L, 2L);
        User reportedUser = User.createParticipant("reported@example.com", EVENT_NOW);
        setField(reportedUser, "id", 2L);
        when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));
        when(userRepository.findById(2L)).thenReturn(Optional.of(reportedUser));

        reportService.resolve(10L, 7L, new ResolveReportRequest(true));

        assertThat(reportedUser.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).findById(2L);
    }

    @Test
    void rejectsResolvingReviewedReport() {
        Report report = report(10L, 1L, 2L);
        report.review(7L, EVENT_NOW.minusMinutes(1));
        when(reportRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.resolve(10L, 8L, new ResolveReportRequest(true)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_ALREADY_REVIEWED));
    }

    @Test
    void returnsChatBetweenReporterAndReportedUser() {
        Report report = report(10L, 1L, 2L);
        ReportChatMessageProjection message = mock(ReportChatMessageProjection.class);
        when(message.getMessageId()).thenReturn(100L);
        when(message.getMatchId()).thenReturn(20L);
        when(message.getSenderId()).thenReturn(1L);
        when(message.getContent()).thenReturn("message");
        when(message.getSentAt()).thenReturn(EVENT_NOW);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(reportRepository.findChatMessages(1L, 2L)).thenReturn(List.of(message));

        var responses = reportService.getChat(10L);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.messageId()).isEqualTo(100L);
            assertThat(response.matchId()).isEqualTo(20L);
            assertThat(response.senderId()).isEqualTo(1L);
            assertThat(response.content()).isEqualTo("message");
        });
    }

    @Test
    void missingReportReturnsReportNotFound() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.get(99L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REPORT_NOT_FOUND));
    }

    private static Report report(Long reportId, Long reporterId, Long reportedUserId) {
        Report report = Report.create(reporterId, reportedUserId, "사유", null, EVENT_NOW.minusHours(1));
        setField(report, "id", reportId);
        return report;
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
}
