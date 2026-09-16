package com.facecook.mission.service;

import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.mission.entity.MissionTemplate;
import com.facecook.mission.repository.MatchMissionAssignmentRepository;
import com.facecook.mission.repository.MatchMissionRepository;
import com.facecook.mission.repository.MissionTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionAssignmentWriterTest {

    private MatchMissionRepository matchRepository;
    private MatchMissionAssignmentRepository assignmentRepository;
    private MissionTemplateRepository templateRepository;
    private MissionAssignmentWriter writer;

    @BeforeEach
    void setUp() {
        matchRepository = mock(MatchMissionRepository.class);
        assignmentRepository = mock(MatchMissionAssignmentRepository.class);
        templateRepository = mock(MissionTemplateRepository.class);
        writer = new MissionAssignmentWriter(
                matchRepository,
                assignmentRepository,
                templateRepository,
                Clock.fixed(Instant.parse("2026-09-30T03:00:00Z"), ZoneOffset.UTC)
        );
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void assignsOnlyCurrentAndFutureStepsAfterLocking() {
        MatchMission mission = match(10L, 2);
        when(matchRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mission));
        when(assignmentRepository.findAllByMatchIdOrderByStep(10L)).thenReturn(List.of());
        when(templateRepository.findAllByStep(2)).thenReturn(List.of(template(2, "two")));
        when(templateRepository.findAllByStep(3)).thenReturn(List.of(template(3, "three")));

        var assignments = writer.assignWithLock(10L);

        assertThat(assignments).extracting(MatchMissionAssignment::getStep).containsExactly(2, 3);
        verify(templateRepository, never()).findAllByStep(1);
        verify(assignmentRepository, times(2)).save(any());
    }

    @Test
    void rechecksAssignmentsUnderLockAndFillsOnlyStillMissingSteps() {
        MatchMission mission = match(10L, 1);
        MatchMissionAssignment existing = assignment(10L, 1, "existing");
        when(matchRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mission));
        when(assignmentRepository.findAllByMatchIdOrderByStep(10L)).thenReturn(List.of(existing));
        when(templateRepository.findAllByStep(2)).thenReturn(List.of(template(2, "two")));
        when(templateRepository.findAllByStep(3)).thenReturn(List.of(template(3, "three")));

        var assignments = writer.assignWithLock(10L);

        assertThat(assignments).hasSize(3);
        assertThat(assignments.getFirst()).isSameAs(existing);
        verify(assignmentRepository, times(2)).save(any());
    }

    @Test
    void doesNotAssignAnyStepForCompletedHistoricalMatch() {
        MatchMission mission = match(10L, MatchMission.COMPLETED_STEP);
        when(matchRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(mission));
        when(assignmentRepository.findAllByMatchIdOrderByStep(10L)).thenReturn(List.of());

        assertThat(writer.assignWithLock(10L)).isEmpty();

        verify(templateRepository, never()).findAllByStep(anyInt());
        verify(assignmentRepository, never()).save(any());
    }

    private static MatchMission match(Long id, int currentStep) {
        MatchMission match = newInstance(MatchMission.class);
        setField(match, "matchId", id);
        setField(match, "currentStep", currentStep);
        return match;
    }

    private static MissionTemplate template(int step, String content) {
        MissionTemplate template = newInstance(MissionTemplate.class);
        setField(template, "step", step);
        setField(template, "content", content);
        return template;
    }

    private static MatchMissionAssignment assignment(Long matchId, int step, String content) {
        MatchMissionAssignment assignment = newInstance(MatchMissionAssignment.class);
        setField(assignment, "matchId", matchId);
        setField(assignment, "step", step);
        setField(assignment, "template", template(step, content));
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
