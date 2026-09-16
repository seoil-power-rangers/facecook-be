package com.facecook.mission.service;

import com.facecook.mission.entity.MatchMission;
import com.facecook.mission.entity.MatchMissionAssignment;
import com.facecook.mission.entity.MissionTemplate;
import com.facecook.mission.repository.MatchMissionAssignmentRepository;
import com.facecook.mission.repository.MatchMissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionAssignmentServiceTest {

    private MatchMissionRepository matchRepository;
    private MatchMissionAssignmentRepository assignmentRepository;
    private MissionAssignmentWriter assignmentWriter;
    private MissionAssignmentService service;

    @BeforeEach
    void setUp() {
        matchRepository = mock(MatchMissionRepository.class);
        assignmentRepository = mock(MatchMissionAssignmentRepository.class);
        assignmentWriter = mock(MissionAssignmentWriter.class);
        service = new MissionAssignmentService(matchRepository, assignmentRepository, assignmentWriter);
    }

    @Test
    void returnsExistingAssignmentsWithoutLockWhenAllRequiredStepsExist() {
        MatchMission mission = match(10L, 2);
        List<MatchMissionAssignment> assignments = List.of(
                assignment(10L, 3, "three"),
                assignment(10L, 2, "two")
        );
        when(assignmentRepository.findAllByMatchIdOrderByStep(10L)).thenReturn(assignments);
        when(matchRepository.findById(10L)).thenReturn(Optional.of(mission));

        var result = service.assignIfAbsent(10L);

        assertThat(result).extracting(MatchMissionAssignment::getStep).containsExactly(2, 3);
        verify(matchRepository, never()).findByIdForUpdate(10L);
        verify(assignmentWriter, never()).assignWithLock(10L);
    }

    @Test
    void delegatesToLockedWriterOnlyWhenRequiredAssignmentIsMissing() {
        MatchMission mission = match(10L, 2);
        List<MatchMissionAssignment> existing = List.of(assignment(10L, 2, "two"));
        List<MatchMissionAssignment> completed = List.of(
                assignment(10L, 2, "two"),
                assignment(10L, 3, "three")
        );
        when(assignmentRepository.findAllByMatchIdOrderByStep(10L)).thenReturn(existing);
        when(matchRepository.findById(10L)).thenReturn(Optional.of(mission));
        when(assignmentWriter.assignWithLock(10L)).thenReturn(completed);

        assertThat(service.assignIfAbsent(10L)).isEqualTo(completed);

        verify(assignmentWriter).assignWithLock(10L);
    }

    @Test
    void completedMatchNeedsNoHistoricalAssignments() {
        MatchMission mission = match(10L, MatchMission.COMPLETED_STEP);
        when(assignmentRepository.findAllByMatchIdOrderByStep(10L)).thenReturn(List.of());
        when(matchRepository.findById(10L)).thenReturn(Optional.of(mission));

        assertThat(service.assignIfAbsent(10L)).isEmpty();

        verify(assignmentWriter, never()).assignWithLock(10L);
    }

    @Test
    void reusesAlreadyLockedMissionForCompletionFlow() {
        MatchMission mission = match(10L, 1);
        List<MatchMissionAssignment> assignments = List.of(assignment(10L, 1, "one"));
        when(assignmentWriter.assignWithLockedMission(mission)).thenReturn(assignments);

        assertThat(service.assignIfAbsent(mission)).isEqualTo(assignments);

        verify(assignmentWriter).assignWithLockedMission(mission);
        verify(matchRepository, never()).findByIdForUpdate(10L);
    }

    private static MatchMission match(Long id, int currentStep) {
        MatchMission match = newInstance(MatchMission.class);
        setField(match, "matchId", id);
        setField(match, "currentStep", currentStep);
        return match;
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
