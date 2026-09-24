package com.facecook.cook.dto;

import java.util.List;

/** {@code GET /api/cooks} 응답: 내가 보낸 콕, 받은 콕(내가 거절한 것은 빠짐), 사용량. */
public record CookListResponse(
        List<CookItemResponse> sent,
        List<CookItemResponse> received,
        CookUsageResponse usage
) {
}
