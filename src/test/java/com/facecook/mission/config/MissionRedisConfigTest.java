package com.facecook.mission.config;

import com.facecook.mission.redis.MissionEventSubscriber;
import com.facecook.mission.redis.MissionRedisChannels;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MissionRedisConfigTest {

    @Test
    void registersMissionSubscriberOnSharedChatContainer() {
        RedisMessageListenerContainer sharedContainer = mock(RedisMessageListenerContainer.class);
        MissionEventSubscriber subscriber = mock(MissionEventSubscriber.class);
        MissionRedisConfig config = new MissionRedisConfig(sharedContainer, subscriber);
        ArgumentCaptor<ChannelTopic> topicCaptor = ArgumentCaptor.forClass(ChannelTopic.class);

        config.registerMissionSubscriber();

        verify(sharedContainer).addMessageListener(
                org.mockito.ArgumentMatchers.eq(subscriber),
                topicCaptor.capture()
        );
        assertThat(topicCaptor.getValue().getTopic()).isEqualTo(MissionRedisChannels.EVENTS);
    }
}
