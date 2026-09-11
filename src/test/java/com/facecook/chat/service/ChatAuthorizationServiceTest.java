package com.facecook.chat.service;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.cook.entity.MatchInfo;
import com.facecook.cook.repository.MatchInfoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAuthorizationServiceTest {

    private final MatchInfoRepository matchInfoRepository = mock(MatchInfoRepository.class);
    private final ChatAuthorizationService service = new ChatAuthorizationService(matchInfoRepository);

    @Test
    void reusesCookMatchInfoIncludesForParticipantCheck() {
        MatchInfo matchInfo = MatchInfo.create(1L, 2L, LocalDateTime.of(2026, 9, 30, 12, 0));
        when(matchInfoRepository.findById(20L)).thenReturn(Optional.of(matchInfo));

        assertThat(service.requireParticipant(20L, 2L)).isSameAs(matchInfo);
    }

    @Test
    void rejectsUserOutsideMatch() {
        MatchInfo matchInfo = MatchInfo.create(1L, 2L, LocalDateTime.of(2026, 9, 30, 12, 0));
        when(matchInfoRepository.findById(20L)).thenReturn(Optional.of(matchInfo));

        assertThatThrownBy(() -> service.requireParticipant(20L, 3L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
