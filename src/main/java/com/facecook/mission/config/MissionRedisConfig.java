package com.facecook.mission.config;

import com.facecook.mission.redis.MissionEventSubscriber;
import com.facecook.mission.redis.MissionRedisChannels;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class MissionRedisConfig {

    @Bean
    RedisMessageListenerContainer missionRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MissionEventSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(MissionRedisChannels.EVENTS));
        return container;
    }
}
