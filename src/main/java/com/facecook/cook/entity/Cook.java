package com.facecook.cook.entity;

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

    public static Cook pending(Long senderId, Long receiverId, LocalDateTime sentAt) {
        return new Cook(senderId, receiverId, sentAt);
    }

    /**
     * 예전에는 보낸 지 1시간이 지나면 만료됐다. 지금은 시간 제한이 없다.
     * 호출부는 그대로 두고, 맞콕하거나 취소하기 전까지는 pending을 유지한다.
     */
    public boolean expireIfOverdue(LocalDateTime now) {
        return false;
    }

    public boolean isPending() {
        return status == CookStatus.PENDING;
    }

    public void match(Long matchedId) {
        this.matchId = matchedId;
        this.status = CookStatus.MATCHED;
    }

    public void cancel() {
        this.status = CookStatus.CANCELLED;
    }

    public Long otherUserId(Long userId) {
        return senderId.equals(userId) ? receiverId : senderId;
    }
}
