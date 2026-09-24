package com.facecook.match.dto;

import com.facecook.match.entity.MatchInfo;
import com.facecook.profile.dto.ProfileResponse;

import java.time.LocalDateTime;

/**
 * 매칭 목록·상세의 한 항목. {@code partner}는 상대 프로필, {@code recentMessage}는 마지막 메시지(없으면 null),
 * {@code unreadCount}는 내가 마지막으로 읽은 뒤 상대가 보낸 메시지 수. 세 값 모두 {@code MatchService}가
 * 매칭 개수와 무관하게 쿼리 한 번씩으로 모아서 채운다.
 */
public record MatchResponse(
        Long matchId,
        LocalDateTime matchedAt,
        ProfileResponse partner,
        RecentMessageResponse recentMessage,
        long unreadCount
) {
    public static MatchResponse from(
            MatchInfo matchInfo,
            ProfileResponse partner,
            RecentMessageResponse recentMessage,
            long unreadCount
    ) {
        return new MatchResponse(matchInfo.getId(), matchInfo.getMatchedAt(), partner, recentMessage, unreadCount);
    }
}
