package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.dto.PushSubscriptionRequest;
import com.facecook.push.entity.PushSubscription;
import com.facecook.push.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
     * 갱신, 없으면 새로 생성해서 저장한다. 이 메서드는 일부러
     * {@code @Transactional}을 걸지 않는다 — 조회-후-저장 사이에
     * 동시성 보호가 없어서, 같은 userId+endpoint로 정확히 동시에 두
     * 요청이 오면 둘 다 "기존 구독 없음"으로 보고 각자 INSERT를 시도할
     * 수 있다. 이때 나중에 커밋되는 쪽이 DB unique 제약({@code
     * uq_push_user_endpoint})에 막혀 {@link DataIntegrityViolationException}이
     * 나는데, {@code save}가 각자 자기 트랜잭션(Spring Data JPA 기본)을
     * 가져야 그 실패가 이 메서드 전체를 롤백시키지 않고 재조회로 이어갈
     * 수 있다 — 같은 트랜잭션 안이었다면 제약 위반 이후 그 트랜잭션은
     * 더 쓸 수 없다.</p>
     *
     * <p>예외 없음(정상 경로에서는). unique 제약을 위반했는데 재조회해도
     * 없으면(설명하기 어려운 상태) 원래 예외를 다시 던진다.</p>
     *
     * @see #unsubscribe(Long)
     */
    public void subscribe(Long userId, PushSubscriptionRequest request) {
        try {
            save(userId, request, findExisting(userId, request));
        } catch (DataIntegrityViolationException exception) {
            PushSubscription existing = findExisting(userId, request)
                    .orElseThrow(() -> exception);
            save(userId, request, Optional.of(existing));
        }
    }

    private Optional<PushSubscription> findExisting(Long userId, PushSubscriptionRequest request) {
        return pushSubscriptionRepository.findByUserIdAndEndpoint(userId, request.endpoint());
    }

    private void save(Long userId, PushSubscriptionRequest request, Optional<PushSubscription> existing) {
        PushSubscription subscription = existing.orElseGet(() -> PushSubscription.create(
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
