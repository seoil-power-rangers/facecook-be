package com.facecook.push.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facecook.push.dto.PushNotificationPayload;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 웹 푸시를 실제로 내보내는 최하단 계층. best-effort로 동작한다 — 이
 * 클래스에서 나는 실패는 절대 밖으로 던지지 않고 로그만 남긴다(콕·매칭
 * 처리 같은 원래 하려던 작업이 알림 실패 때문에 실패로 바뀌면 안 되기
 * 때문. 호출부인 {@link ParticipantPushNotificationService}도 같은
 * 이유로 이 클래스의 예외를 다시 한번 흡수한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeliveryService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushGateway webPushGateway;
    private final ObjectMapper objectMapper;
    private final PushDeliveryMonitor monitor;

    /**
     * userId가 등록해둔 모든 브라우저 구독 각각에 payload를 보낸다(기기가
     * 여러 개면 전부).
     *
     * <p>전제조건: 없음(구독이 하나도 없으면 아무 일도 안 하고 끝).</p>
     *
     * <p>부작용: 전용 실행기({@code pushExecutor})에서 요청 스레드와
     * 분리돼 비동기로 돈다 — 콕·채팅 전송의 응답이 웹 푸시 HTTP 왕복을
     * 기다리지 않는다(facecook-be#82). 구독 조회와 만료 구독 삭제는 각각
     * Spring Data가 알아서 여는 짧은 트랜잭션이고, 그 사이 웹 푸시 HTTP
     * 호출은 트랜잭션 밖에서 돈다 — DB 커넥션을 외부 호출 동안 붙들고
     * 있지 않는다. 구독마다 개별로 발송을 시도해서 하나가 실패해도
     * 나머지는 계속 보낸다. 만료된 구독(브라우저가 알림 권한을 끊는 등)은
     * 응답에서 확인되면 그 자리에서 DB에서 삭제한다.</p>
     *
     * <p>예외 없음 — 페이로드 직렬화 실패, 개별 발송 실패 전부 로그만
     * 남기고 삼킨다. 발송 실패(제한 시간 초과 포함)는 {@link PushDeliveryMonitor}에도
     * 센다. 전용 실행기 큐가 가득 차면 이 호출 자체가
     * {@link java.util.concurrent.RejectedExecutionException}을 던질 수
     * 있다 — 호출부({@link ParticipantPushNotificationService})가
     * 흡수한다.</p>
     */
    @Async("pushExecutor")
    public void sendToUser(Long userId, PushNotificationPayload payload) {
        String serializedPayload;
        try {
            serializedPayload = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.error("푸시 페이로드 직렬화에 실패했습니다. userId={}, type={}", userId, payload.type(), exception);
            return;
        }

        for (PushSubscription subscription : pushSubscriptionRepository.findAllByUserId(userId)) {
            send(subscription, serializedPayload);
        }
    }

    private void send(PushSubscription subscription, String payload) {
        try {
            PushDeliveryResult result = webPushGateway.send(subscription, payload);
            if (result.successful()) {
                return;
            }
            if (result.subscriptionExpired()) {
                pushSubscriptionRepository.delete(subscription);
                log.info(
                        "만료된 푸시 구독을 삭제했습니다. userId={}, subscriptionId={}, status={}",
                        subscription.getUserId(), subscription.getId(), result.statusCode()
                );
                return;
            }
            monitor.recordFailed();
            log.warn(
                    "웹 푸시 서버가 발송을 거부했습니다. userId={}, subscriptionId={}, status={}, reason={}",
                    subscription.getUserId(), subscription.getId(), result.statusCode(), result.reason()
            );
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            monitor.recordFailed();
            log.warn(
                    "웹 푸시 발송에 실패했습니다. userId={}, subscriptionId={}",
                    subscription.getUserId(), subscription.getId(), exception
            );
        }
    }
}
