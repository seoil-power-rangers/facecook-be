package com.facecook.mission.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.facecook.mission.dto.MissionProgressResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionEventSubscriberTest {

    @Test
    void forwardsRedisEventToMatchMissionTopic() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        MissionEventSubscriber subscriber = new MissionEventSubscriber(objectMapper, messagingTemplate);
        MissionProgressResponse progress =
                new MissionProgressResponse(20L, 2, "새 STEP 미션", null, null, null);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(
                objectMapper.writeValueAsString(progress).getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        verify(messagingTemplate).convertAndSend("/topic/mission/20", progress);
    }
}
