package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 브라우저 웹 푸시 구독의 등록·해지, VAPID 공개키 제공.
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final VapidProperties vapidProperties;

    /**
     * 브라우저가 푸시 구독을 만들 때 필요한 VAPID 공개키를 반환한다.
     *
     * <p>전제조건: 서버에 VAPID 키가 설정돼 있어야 함.</p>
     *
     * <p>부작용: 없음.</p>
     *
     * <p>예외: {@code IllegalStateException}(설정 누락 — 다른 메서드들과
     * 달리 {@code ApiException}이 아니라서 500으로 응답된다).</p>
     */
    public String getVapidPublicKey() {
        String publicKey = vapidProperties.publicKey();
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException("VAPID 공개키가 설정되지 않았습니다.");
        }
        return publicKey;
    }

    /**
     * userId의 브라우저 구독을 등록한다(같은 endpoint로 이미 등록돼
     * 있으면 키 값만 최신으로 갱신 — upsert).
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 기존 구독(userId+endpoint 동일) 조회 후 있으면 키
     * 갱신, 없으면 새로 생성해서 저장한다. 이 조회-후-저장 사이에
     * 동시성 보호가 없다 — 같은 userId+endpoint로 정확히 동시에 두
     * 요청이 오면 DB unique 제약({@code uq_push_user_endpoint})이 뒤늦게
     * 막지만 이 메서드는 그 예외를 따로 잡지 않는다(500으로 응답).</p>
     *
     * <p>예외 없음(정상 경로에서는).</p>
     *
     * @see #unsubscribe(Long)
     */
    @Transactional
    public void subscribe(Long userId, PushSubscriptionRequest request) {
        PushSubscription subscription = pushSubscriptionRepository
                .findByUserIdAndEndpoint(userId, request.endpoint())
                .orElseGet(() -> PushSubscription.create(
                        userId,
                        request.endpoint(),
                        request.keys().p256dh(),
                        request.keys().auth()
                ));
        subscription.updateKeys(request.keys().p256dh(), request.keys().auth());
        pushSubscriptionRepository.save(subscription);
    }

    /**
     * userId의 구독을 전부(기기 여러 개면 전부) 삭제한다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: {@code push_subscription} 행을 userId 기준으로 전부
     * 지운다. 구독이 하나도 없어도 에러 없이 그냥 0건 삭제로 끝난다.</p>
     *
     * <p>예외 없음.</p>
     *
     * @see #subscribe(Long, PushSubscriptionRequest)
     */
    @Transactional
    public void unsubscribe(Long userId) {
        pushSubscriptionRepository.deleteAllByUserId(userId);
    }
}
