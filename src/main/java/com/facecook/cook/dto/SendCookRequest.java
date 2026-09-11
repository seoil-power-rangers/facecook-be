package com.facecook.cook.dto;

import jakarta.validation.constraints.NotNull;

public record SendCookRequest(
        @NotNull(message = "receiverId는 필수입니다.") Long receiverId
) {
}
