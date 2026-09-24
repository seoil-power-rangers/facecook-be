package com.facecook.cook.dto;

import com.facecook.cook.entity.Cook;
import com.facecook.profile.dto.ProfileResponse;

import java.time.LocalDateTime;

/**
 * 콕 목록의 한 항목. 보낸 목록이든 받은 목록이든 {@code userId}·{@code profile}은 항상 "상대방"이다
 * ({@code Cook#otherUserId}). {@code status}는 {@code pending}/{@code matched}/{@code rejected} 등 소문자,
 * {@code matchId}는 맞콕으로 매칭됐을 때만 있다.
 */
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
