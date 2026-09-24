package com.facecook.mission.config;

import com.facecook.mission.redis.MissionEventSubscriber;
import com.facecook.mission.redis.MissionRedisChannels;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 미션 진행 이벤트용 Redis 구독을 붙인다. 채팅이 만든 리스너 컨테이너
 * ({@code chatRedisMessageListenerContainer}, {@code RedisChatConfig})에 미션 채널
 * ({@link MissionRedisChannels#EVENTS}) 리스너를 하나 더 등록한다 — Redis 구독 연결을 하나로 같이 쓴다.
 *
 * <p>{@code @PostConstruct}: 이 설정 객체가 만들어지고 의존 빈이 모두 들어온 직후 한 번 실행된다.
 * {@code @Qualifier}는 어느 빈을 받을지 이름으로 지정한다(채팅용 컨테이너를 같이 쓴다는 의도를 드러낸다).</p>
 */
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
