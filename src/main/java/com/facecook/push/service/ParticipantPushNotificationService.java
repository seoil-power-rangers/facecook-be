package com.facecook.push.service;

import com.facecook.chat.redis.ChatPresenceService;
import com.facecook.push.dto.PushNotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantPushNotificationService {

    private final ChatPresenceService chatPresenceService;
    private final PushDeliveryService pushDeliveryService;

    public void cookReceived(Long receiverId) {
        sendAfterCommit(receiverId, new PushNotificationPayload(
                "COOK_RECEIVED",
                "새로운 콕이 도착했어요",
                "누군가 회원님을 콕 찔렀어요.",
                "/main"
        ));
    }

    public void matchCreated(Long userId, Long matchId) {
        sendAfterCommit(userId, new PushNotificationPayload(
                "MATCH_CREATED",
                "매칭이 성사됐어요",
                "서로의 마음이 통했어요. 지금 대화를 시작해 보세요.",
                "/match/" + matchId + "/matched"
        ));
    }

    public void chatMessageReceived(Long receiverId, Long matchId) {
        sendAfterCommit(receiverId, new PushNotificationPayload(
                "CHAT_MESSAGE_RECEIVED",
                "새 메시지가 도착했어요",
                "매칭 상대가 새로운 메시지를 보냈어요.",
                "/match/" + matchId
        ));
    }

    private void sendAfterCommit(Long userId, PushNotificationPayload payload) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendIfOffline(userId, payload);
                }
            });
            return;
        }
        sendIfOffline(userId, payload);
    }

    private void sendIfOffline(Long userId, PushNotificationPayload payload) {
        try {
            if (chatPresenceService.isConnected(userId)) {
                return;
            }
            pushDeliveryService.sendToUser(userId, payload);
        } catch (RuntimeException exception) {
            // 푸시는 best-effort 부가 기능이다. 알림 인프라 장애가 이미 성공한
            // 콕/매칭/메시지 처리를 실패로 바꾸지 않도록 경계에서 흡수한다.
            log.warn("참가자 푸시 처리에 실패했습니다. userId={}, type={}", userId, payload.type(), exception);
        }
    }
}
