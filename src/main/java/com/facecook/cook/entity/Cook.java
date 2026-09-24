package com.facecook.cook.entity;

import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A가 B에게 보낸 콕 하나({@code cook} 테이블). 같은 사람에게는 한 번만 보낼 수 있다
 * ({@code (sender_id, receiver_id)} unique 제약).
 *
 * <p>상태가 바뀌는 길은 정해져 있고, 바꾸는 메서드가 규칙을 직접 검사한다:</p>
 * <pre>
 *  pending ──(상대도 나에게 보냄: 맞콕)──▶ matched   {@link #match}
 *     │ ├──(보낸 사람이 취소)───────────▶ cancelled {@link #cancel}
 *     │ └──(받은 사람이 거절)───────────▶ rejected  {@link #reject}
 *  expired: 예전 버전이 남긴 상태. 지금은 새로 만들지 않고 읽기만 한다.
 * </pre>
 *
 * <p>규칙을 서비스가 아니라 엔티티 메서드에 둔 이유: 상태를 바꾸는 모든 경로가 같은 검사를 거치게 하려는 것이다.
 * 이 엔티티에는 setter가 없어서 상태를 바꾸려면 이 메서드들을 불러야 한다.</p>
 */
@Getter
@Entity
@Table(name = "cook")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cook_id")
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "match_id")
    private Long matchId;

    @Convert(converter = CookStatusConverter.class)
    @Column(nullable = false, length = 20)
    private CookStatus status;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    private Cook(Long senderId, Long receiverId, LocalDateTime sentAt) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.status = CookStatus.PENDING;
        this.sentAt = sentAt;
    }

    /** 새로 보낸 콕. 항상 대기(pending)로 시작한다. 호출: {@code CookService.savePendingCook}. */
    public static Cook pending(Long senderId, Long receiverId, LocalDateTime sentAt) {
        return new Cook(senderId, receiverId, sentAt);
    }

    public boolean isPending() {
        return status == CookStatus.PENDING;
    }

    public boolean isRejected() {
        return status == CookStatus.REJECTED;
    }

    /** 맞콕으로 매칭이 성사됐을 때 두 콕 모두에 부른다({@code CookService.markCooksMatched}). */
    public void match(Long matchedId) {
        this.matchId = matchedId;
        this.status = CookStatus.MATCHED;
    }

    /**
     * 보낸 사람이 자신의 콕을 취소한다. 이미 취소된 콕을 다시 취소해도 상태는 그대로 두고 성공한다.
     *
     * <p>전제조건: {@code userId}가 이 콕의 보낸 사람이고, 매칭·만료·거절된 콕이 아니다. 거절된 콕을
     * 취소로 덮어쓰면 거절 상태가 풀려 거절한 상대에게 다시 콕을 보낼 수 있으므로 막는다.</p>
     *
     * <p>부작용: 상태를 {@link CookStatus#CANCELLED}로 바꾼다. 저장은 호출한 트랜잭션의 더티 체킹에 맡긴다.</p>
     *
     * <p>예외: {@code FORBIDDEN}(보낸 사람이 아님), {@code ALREADY_MATCHED}, {@code ALREADY_EXPIRED}
     * (레거시 만료 행), {@code ALREADY_REJECTED}. 검사 순서는 보낸 사람 → 매칭 → 만료 → 거절이다.</p>
     */
    public void cancel(Long userId) {
        if (!senderId.equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (status == CookStatus.MATCHED) {
            throw new ApiException(ErrorCode.ALREADY_MATCHED);
        }
        if (status == CookStatus.EXPIRED) {
            throw new ApiException(ErrorCode.ALREADY_EXPIRED);
        }
        if (status == CookStatus.REJECTED) {
            throw new ApiException(ErrorCode.ALREADY_REJECTED);
        }
        this.status = CookStatus.CANCELLED;
    }

    /**
     * 받은 사람이 자신이 받은 콕을 거절한다. 이미 거절한 콕을 다시 거절해도 상태는 그대로 두고 성공한다.
     *
     * <p>전제조건: {@code userId}가 이 콕의 받은 사람이다.</p>
     *
     * <p>부작용: 대기 중인 콕이면 상태를 {@link CookStatus#REJECTED}로 바꾼다. 저장은 호출한 트랜잭션의
     * 더티 체킹에 맡기고, 푸시나 사용 횟수 환급은 하지 않는다.</p>
     *
     * <p>예외: {@code FORBIDDEN}(받은 사람이 아님), {@code ALREADY_MATCHED}, {@code NOT_FOUND}(이미 취소된 콕 —
     * 받은 사람에게는 없는 콕과 같다), {@code ALREADY_EXPIRED}(레거시 만료 행). 검사 순서는 받은 사람 → 상태다.</p>
     */
    public void reject(Long userId) {
        if (!receiverId.equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        switch (status) {
            case PENDING -> this.status = CookStatus.REJECTED;
            case REJECTED -> {
            }
            case MATCHED -> throw new ApiException(ErrorCode.ALREADY_MATCHED);
            case CANCELLED -> throw new ApiException(ErrorCode.NOT_FOUND);
            case EXPIRED -> throw new ApiException(ErrorCode.ALREADY_EXPIRED);
            default -> throw new IllegalStateException("거절 규칙이 정해지지 않은 콕 상태: " + status);
        }
    }

    /** userId 입장에서 상대방의 ID. 목록 응답에서 "상대 프로필"을 채울 때 쓴다. */
    public Long otherUserId(Long userId) {
        return senderId.equals(userId) ? receiverId : senderId;
    }
}
