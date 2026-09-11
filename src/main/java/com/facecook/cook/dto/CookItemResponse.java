package com.facecook.cook.dto;

import com.facecook.cook.entity.Cook;
import com.facecook.profile.dto.ProfileResponse;

import java.time.LocalDateTime;

public record CookItemResponse(
        Long cookId,
        Long userId,
        ProfileResponse profile,
        String status,
        LocalDateTime sentAt,
        Long matchId
) {
    public static CookItemResponse from(Cook cook, Long currentUserId, ProfileResponse profile) {
        return new CookItemResponse(
                cook.getId(),
                cook.otherUserId(currentUserId),
                profile,
                cook.getStatus().value(),
                cook.getSentAt(),
                cook.getMatchId()
        );
    }
}
