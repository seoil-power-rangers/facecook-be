package com.facecook.chat.redis;

/** 채팅용 Redis 채널 이름. 발행({@link ChatMessagePublisher})과 구독({@code RedisChatConfig})이 같은 상수를 쓴다. */
public final class ChatRedisChannels {

    public static final String MESSAGES = "facecook:chat:messages";

    private ChatRedisChannels() {
    }
}
