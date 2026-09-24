package com.facecook.mission.event;

import com.facecook.mission.redis.MissionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 미션 완료가 DB에 커밋된 뒤, 새 진행 상태를 Redis로 발행해 참가자 화면에 실시간으로 알린다.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}: 이벤트를 발행한 트랜잭션이 <b>커밋된 뒤에만</b>
 * 실행한다. 롤백되면 실행되지 않아서, 저장되지 않은 진행을 화면에 먼저 보여 주는 일이 없다.
 * {@code @Async("missionEventExecutor")}: 별도 스레드에서 돌아서, Redis가 느려도 관리자의 완료 요청 응답이
 * 기다리지 않는다.</p>
 *
 * <p>흐름: 완료 커밋 → 이 리스너 → {@code MissionEventPublisher}(Redis 채널) → 모든 서버의
 * {@code MissionEventSubscriber} → {@code /topic/mission/{matchId}} 구독자. 채팅과 같은 서버 간 중계 방식이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Async("missionEventExecutor")
public class MissionProgressCommittedListener {

    private final MissionEventPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(MissionProgressCommittedEvent event) {
        try {
            publisher.publish(event.progress());
        } catch (RuntimeException exception) {
            // DB 완료 처리는 이미 커밋됐다. Redis 장애 때문에 성공한 관리자
            // 요청을 실패로 보이게 하지 않고, 참가자는 REST 재조회로 복구한다.
            log.error("커밋된 미션 진행상황을 Redis에 발행하지 못했습니다.", exception);
        }
    }
}
