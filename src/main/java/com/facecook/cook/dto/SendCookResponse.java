package com.facecook.cook.dto;

import com.facecook.cook.entity.Cook;

import java.time.LocalDateTime;

public record SendCookResponse(
        Long cookId,
        Long receiverId,
        String status,
        LocalDateTime sentAt,
        boolean matched,
        Long matchId
) {
    public static SendCookResponse from(Cook cook) {
        return new SendCookResponse(
                cook.getId(),
                cook.getReceiverId(),
                cook.getStatus().value(),
                cook.getSentAt(),
                cook.getMatchId() != null,
                cook.getMatchId()
        );
    }
}
