package com.facecook.cook.dto;

import jakarta.validation.constraints.NotNull;

/** {@code POST /api/cooks} 요청 본문. 보내는 사람은 세션에서 정하므로 받는 사람만 받는다. */
public record SendCookRequest(
        @NotNull(message = "receiverId는 필수입니다.") Long receiverId
) {
}
