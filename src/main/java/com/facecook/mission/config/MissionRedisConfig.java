package com.facecook.mission.config;

import com.facecook.mission.redis.MissionEventSubscriber;
import com.facecook.mission.redis.MissionRedisChannels;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class MissionRedisConfig {

    private final RedisMessageListenerContainer listenerContainer;
    private final MissionEventSubscriber subscriber;

    public MissionRedisConfig(
            @Qualifier("chatRedisMessageListenerContainer") RedisMessageListenerContainer listenerContainer,
            MissionEventSubscriber subscriber
    ) {
        this.listenerContainer = listenerContainer;
        this.subscriber = subscriber;
    }

    @PostConstruct
    void registerMissionSubscriber() {
        listenerContainer.addMessageListener(
                subscriber,
                new ChannelTopic(MissionRedisChannels.EVENTS)
        );
    }
}
