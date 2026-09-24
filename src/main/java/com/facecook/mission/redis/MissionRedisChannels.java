package com.facecook.mission.redis;

/** 미션용 Redis 채널 이름. 발행과 구독이 같은 상수를 쓴다. */
public final class MissionRedisChannels {
    public static final String EVENTS = "facecook:mission:events";

    private MissionRedisChannels() {
    }
}
