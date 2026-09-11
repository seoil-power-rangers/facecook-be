package com.facecook.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank(message = "endpoint는 필수입니다.")
        @Size(max = 500, message = "endpoint는 500자 이하여야 합니다.")
        String endpoint,

        @NotNull(message = "keys는 필수입니다.")
        @Valid
        Keys keys
) {
    public record Keys(
            @NotBlank(message = "keys.p256dh는 필수입니다.")
            @Size(max = 255, message = "keys.p256dh는 255자 이하여야 합니다.")
            String p256dh,

            @NotBlank(message = "keys.auth는 필수입니다.")
            @Size(max = 255, message = "keys.auth는 255자 이하여야 합니다.")
            String auth
    ) {
    }
}
