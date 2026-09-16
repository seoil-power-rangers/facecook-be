package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.dto.AdminMissionProgressResponse;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.mission.entity.MissionTemplate;
import com.facecook.mission.event.MissionProgressCommittedEvent;
import com.facecook.mission.repository.MatchMissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");

    private MatchMissionRepository repository;
    private MissionAssignmentService assignmentService;
    private MissionAuthorizationService authorizationService;
    private ApplicationEventPublisher eventPublisher;
    private MissionService missionService;

    @BeforeEach
    void setUp() {
        repository = mock(MatchMissionRepository.class);
        assignmentService = mock(MissionAssignmentService.class);
        authorizationService = mock(MissionAuthorizationService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        missionService = new MissionService(
                repository,
                assignmentService,
                authorizationService,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void participantCanReadOwnMatchProgress() {
        MatchMission mission = mission(10L, 1L, 2L, 2);
        when(authorizationService.requireParticipant(10L, 2L)).thenReturn(mission);
        when(assignmentService.assignIfAbsent(10L)).thenReturn(assignments(10L));

        var response = missionService.getProgress(10L, 2L);

        assertThat(response.matchId()).isEqualTo(10L);
        assertThat(response.currentStep()).isEqualTo(2);
        assertThat(response.currentMission()).isEqualTo("STEP 2 미션");
    }

    @Test
    void nonParticipantCannotReadMatchProgress() {
        when(authorizationService.requireParticipant(10L, 3L))
                .thenThrow(new ApiException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> missionService.getProgress(10L, 3L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(assignmentService);
    }

    @Test
    void missingMatchReturnsNotFound() {
        when(authorizationService.requireParticipant(99L, 1L))
                .thenThrow(new ApiException(ErrorCode.NOT_FOUND, "매칭을 찾을 수 없습니다."));

        assertThatThrownBy(() -> missionService.getProgress(99L, 1L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void completesCurrentStepAndOpensNextStep() {
        MatchMission mission = mission(10L, 1L, 2L, 1);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(mission));
        when(assignmentService.assignIfAbsent(mission)).thenReturn(assignments(10L));

        var response = missionService.completeCurrentStep(10L, 7L);

        assertThat(response.currentStep()).isEqualTo(2);
        assertThat(response.step1CompletedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(response.step1CompletedBy()).isEqualTo(7L);
        ArgumentCaptor<MissionProgressCommittedEvent> eventCaptor =
                ArgumentCaptor.forClass(MissionProgressCommittedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().progress().currentMission()).isEqualTo("STEP 2 미션");
    }

    @Test
    void rejectsCompletingAnAlreadyCompletedMission() {
        MatchMission mission = mission(10L, 1L, 2L, 4);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(mission));
        when(assignmentService.assignIfAbsent(mission)).thenReturn(assignments(10L));

        assertThatThrownBy(() -> missionService.completeCurrentStep(10L, 7L))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION);
                    assertThat(exception.getMessage()).contains("이미 모든 STEP");
                });
    }

    @Test
    void listsMissionsNewestMatchFirst() {
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        when(repository.findAll(sortCaptor.capture())).thenReturn(List.of(mission(10L, 1L, 2L, 1)));
        when(assignmentService.assignIfAbsent(10L)).thenReturn(assignments(10L));

        var responses = missionService.getAllProgress();

        assertThat(responses).hasSize(1);
        verify(repository).findAll(sortCaptor.getValue());
        assertThat(sortCaptor.getValue().getOrderFor("matchedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void participantResponseNeverContainsFutureMissionContents() {
        MatchMission mission = mission(10L, 1L, 2L, 1);
        when(authorizationService.requireParticipant(10L, 1L)).thenReturn(mission);
        when(assignmentService.assignIfAbsent(10L)).thenReturn(assignments(10L));

        var response = missionService.getProgress(10L, 1L);

        assertThat(response.currentMission()).isEqualTo("STEP 1 미션");
        assertThat(response.toString()).doesNotContain("STEP 2 미션", "STEP 3 미션");
    }

    @Test
    void skipsBrokenMatchAndReturnsRemainingAdminProgress() {
        MatchMission broken = mission(10L, 1L, 2L, 1);
        MatchMission healthy = mission(20L, 3L, 4L, 1);
        when(repository.findAll(org.mockito.ArgumentMatchers.any(Sort.class)))
                .thenReturn(List.of(broken, healthy));
        when(assignmentService.assignIfAbsent(10L))
                .thenThrow(new IllegalStateException("템플릿 없음"));
        when(assignmentService.assignIfAbsent(20L)).thenReturn(assignments(20L));

        var responses = missionService.getAllProgress();

        assertThat(responses).extracting(AdminMissionProgressResponse::matchId).containsExactly(20L);
    }

    private static MatchMission mission(Long matchId, Long userAId, Long userBId, int currentStep) {
        MatchMission mission = newInstance();
        setField(mission, "matchId", matchId);
        setField(mission, "userAId", userAId);
        setField(mission, "userBId", userBId);
        setField(mission, "matchedAt", LocalDateTime.of(2026, 9, 30, 10, 0));
        setField(mission, "currentStep", currentStep);
        return mission;
    }

    private static MatchMission newInstance() {
        try {
            var constructor = MatchMission.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<MatchMissionAssignment> assignments(Long matchId) {
        return List.of(
                assignment(matchId, 1, "STEP 1 미션"),
                assignment(matchId, 2, "STEP 2 미션"),
                assignment(matchId, 3, "STEP 3 미션")
        );
    }

    private static MatchMissionAssignment assignment(Long matchId, int step, String content) {
        MissionTemplate template = newInstance(MissionTemplate.class);
        setField(template, "step", step);
        setField(template, "content", content);
        MatchMissionAssignment assignment = newInstance(MatchMissionAssignment.class);
        setField(assignment, "matchId", matchId);
        setField(assignment, "step", step);
        setField(assignment, "template", template);
        return assignment;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void setField(MatchMission mission, String name, Object value) {
        setField((Object) mission, name, value);
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
