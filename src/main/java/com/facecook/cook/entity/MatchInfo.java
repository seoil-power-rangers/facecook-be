package com.facecook.cook.entity;

import jakarta.persistence.Column;
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
@Table(name = "match_info")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long id;

    @Column(name = "user_a_id", nullable = false)
    private Long userAId;

    @Column(name = "user_b_id", nullable = false)
    private Long userBId;

    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;

    private MatchInfo(Long firstUserId, Long secondUserId, LocalDateTime matchedAt) {
        this.userAId = Math.min(firstUserId, secondUserId);
        this.userBId = Math.max(firstUserId, secondUserId);
        this.matchedAt = matchedAt;
    }

    public static MatchInfo create(Long firstUserId, Long secondUserId, LocalDateTime matchedAt) {
        return new MatchInfo(firstUserId, secondUserId, matchedAt);
    }

    public boolean includes(Long userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }

    public Long otherUserId(Long userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }
}
