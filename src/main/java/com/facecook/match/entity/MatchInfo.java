package com.facecook.match.entity;

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

/**
 * 성사된 매칭 한 쌍({@code match_info} 테이블). 맞콕 순간 {@code CookService}가 만든다.
 *
 * <p>두 사람을 항상 작은 userId가 A, 큰 userId가 B가 되게 저장한다(생성자 참고). 누가 먼저 콕을 보냈든
 * 같은 두 사람은 같은 (A, B)로 저장돼서, 비교·조회 규칙이 하나로 정리된다. 사람별 값(마지막 읽은 시각)은
 * A용·B용 컬럼이 따로 있다.</p>
 *
 * <p>같은 테이블을 미션 쪽에서는 {@code MatchMission} 엔티티로 따로 매핑한다(미션 단계 컬럼을 다룸).
 * 이 엔티티는 미션 컬럼을 모른다.</p>
 *
 * <p>읽음 시각은 엔티티를 고쳐 저장하지 않고 컬럼 하나만 바꾸는 UPDATE로 올린다 — 두 사람이 동시에 읽어도
 * 서로의 값을 예전 값으로 되돌리지 않게 하기 위해서다({@link MatchInfoRepository#markReadAsUserA}).</p>
 */
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

    @Column(name = "user_a_last_read_at")
    private LocalDateTime userALastReadAt;

    @Column(name = "user_b_last_read_at")
    private LocalDateTime userBLastReadAt;

    private MatchInfo(Long firstUserId, Long secondUserId, LocalDateTime matchedAt) {
        this.userAId = Math.min(firstUserId, secondUserId);
        this.userBId = Math.max(firstUserId, secondUserId);
        this.matchedAt = matchedAt;
    }

    /** 두 사람의 순서와 무관하게 작은 ID를 A로 정해 만든다. */
    public static MatchInfo create(Long firstUserId, Long secondUserId, LocalDateTime matchedAt) {
        return new MatchInfo(firstUserId, secondUserId, matchedAt);
    }

    /** 이 매칭에서 userId가 A쪽(작은 userId)인지. 당사자가 아니면 false이므로 {@link #includes}로 먼저 확인한다. */
    public boolean isUserA(Long userId) {
        return userAId.equals(userId);
    }

    /** userId가 이 매칭의 당사자인지. 매칭·채팅·미션 API의 권한 검사가 이걸 쓴다. */
    public boolean includes(Long userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }

    public Long otherUserId(Long userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    /** userId 쪽의 마지막 읽은 시각(없으면 null). */
    public LocalDateTime lastReadAt(Long userId) {
        return userAId.equals(userId) ? userALastReadAt : userBLastReadAt;
    }
}
