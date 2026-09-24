package com.facecook.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 브라우저가 만든 구독 정보(Web Push 표준 {@code PushSubscription.toJSON()} 모양). {@code endpoint}는 이 브라우저로
 * 알림을 보낼 푸시 서버 주소, {@code keys}는 알림 내용을 이 브라우저만 풀 수 있게 암호화하는 데 쓰는 값이다.
 *
 * <p>{@code keys}의 {@code @Valid}: 안쪽 record({@link Keys})의 검증 어노테이션까지 검사하게 한다.</p>
 */
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
