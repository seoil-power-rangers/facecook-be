package com.facecook.match.dto;

import com.facecook.match.entity.MatchInfo;
import com.facecook.profile.dto.ProfileResponse;

import java.time.LocalDateTime;

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
