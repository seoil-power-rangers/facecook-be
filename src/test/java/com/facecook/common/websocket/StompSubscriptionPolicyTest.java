package com.facecook.common.websocket;

import com.facecook.chat.service.ChatAuthorizationService;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.mission.service.MissionAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StompSubscriptionPolicyTest {

    private final ChatAuthorizationService chatAuthorization = mock(ChatAuthorizationService.class);
    private final MissionAuthorizationService missionAuthorization = mock(MissionAuthorizationService.class);
    private final StompSubscriptionPolicy policy = new StompSubscriptionPolicy(chatAuthorization, missionAuthorization);

    @Test
    void chatTopicRequiresChatParticipant() {
        policy.authorize("/topic/chat/20", 1L);

        verify(chatAuthorization).requireParticipant(20L, 1L);
        verifyNoInteractions(missionAuthorization);
    }

    @Test
    void missionTopicRequiresMissionParticipant() {
        policy.authorize("/topic/mission/20", 1L);

        verify(missionAuthorization).requireParticipant(20L, 1L);
        verifyNoInteractions(chatAuthorization);
    }

    @Test
    void ownChatAckQueueIsAllowedWithoutMatchCheck() {
        policy.authorize("/user/queue/chat-acks", 1L);

        verifyNoInteractions(chatAuthorization, missionAuthorization);
    }

    @Test
    void nonParticipantIsRejectedByTheDomainCheck() {
        doThrow(new ApiException(ErrorCode.FORBIDDEN)).when(chatAuthorization).requireParticipant(20L, 1L);

        assertThatThrownBy(() -> policy.authorize("/topic/chat/20", 1L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "/topic/unknown",
            "/topic/chat",
            "/topic/chat/",
            "/topic/chat/20/extra",
            "/topic/chat/abc",
            "/topic/mission/abc",
            "/queue/other",
            "/user/queue/other",
            "/app/chat/20/send"
    })
    void anythingOutsideTheAllowListIsForbidden(String destination) {
        assertThatThrownBy(() -> policy.authorize(destination, 1L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(chatAuthorization, missionAuthorization);
    }

    @Test
    void matchIdTooLargeForLongIsValidationError() {
        assertThatThrownBy(() -> policy.authorize("/topic/chat/99999999999999999999", 1L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION));
    }
}
