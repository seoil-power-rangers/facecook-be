package com.facecook.mission.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.repository.MatchMissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");

    private MatchMissionRepository repository;
    private MissionService missionService;

    @BeforeEach
    void setUp() {
        repository = mock(MatchMissionRepository.class);
        missionService = new MissionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void participantCanReadOwnMatchProgress() {
        MatchMission mission = mission(10L, 1L, 2L, 2);
        when(repository.findById(10L)).thenReturn(Optional.of(mission));

        var response = missionService.getProgress(10L, 2L);

        assertThat(response.matchId()).isEqualTo(10L);
        assertThat(response.currentStep()).isEqualTo(2);
    }

    @Test
    void nonParticipantCannotReadMatchProgress() {
        when(repository.findById(10L)).thenReturn(Optional.of(mission(10L, 1L, 2L, 1)));

        assertThatThrownBy(() -> missionService.getProgress(10L, 3L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void missingMatchReturnsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> missionService.getProgress(99L, 1L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void completesCurrentStepAndOpensNextStep() {
        MatchMission mission = mission(10L, 1L, 2L, 1);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(mission));

        var response = missionService.completeCurrentStep(10L, 7L);

        assertThat(response.currentStep()).isEqualTo(2);
        assertThat(response.step1CompletedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(response.step1CompletedBy()).isEqualTo(7L);
    }

    @Test
    void rejectsCompletingAnAlreadyCompletedMission() {
        MatchMission mission = mission(10L, 1L, 2L, 4);
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(mission));

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

        var responses = missionService.getAllProgress();

        assertThat(responses).hasSize(1);
        verify(repository).findAll(sortCaptor.getValue());
        assertThat(sortCaptor.getValue().getOrderFor("matchedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
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

    private static void setField(MatchMission mission, String name, Object value) {
        try {
            Field field = MatchMission.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(mission, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
