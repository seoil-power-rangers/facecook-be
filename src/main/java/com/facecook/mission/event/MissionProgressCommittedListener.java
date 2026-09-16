package com.facecook.mission.event;

import com.facecook.mission.redis.MissionEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
