package com.facecook.cook.dto;

import com.facecook.cook.entity.Cook;

import java.time.LocalDateTime;

/**
 * 콕 전송 결과. {@code matched}가 true면 이 콕으로 맞콕이 돼서 바로 매칭이 성사된 것이고,
 * FE는 {@code matchId}로 매칭 축하 화면으로 이동한다.
 */
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
