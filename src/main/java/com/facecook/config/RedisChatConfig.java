package com.facecook.config;

import com.facecook.chat.redis.ChatMessageSubscriber;
import com.facecook.chat.redis.ChatRedisChannels;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 채팅 메시지를 서버 2대 사이에 전달하는 Redis 구독 설정.
 *
 * <p>사용자 A는 서버1, B는 서버2에 WebSocket으로 붙어 있을 수 있다. 메시지를
 * 저장한 서버가 Redis 채널({@code ChatRedisChannels.MESSAGES})에 발행하면, 모든
 * 서버의 {@code ChatMessageSubscriber}가 받아서 각자 붙어 있는 사용자에게
 * 내려보낸다. 이 빈이 그 "받는 쪽" 연결을 서버 기동 시 열어 둔다.</p>
 */
@Configuration
public class RedisChatConfig {

    @Bean
    RedisMessageListenerContainer chatRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatMessageSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(ChatRedisChannels.MESSAGES));
        return container;
    }
}
