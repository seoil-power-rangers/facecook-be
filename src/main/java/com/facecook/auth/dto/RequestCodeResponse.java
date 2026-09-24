package com.facecook.auth.dto;

/**
 * 인증코드 발급 응답. FE가 "5:00 남음" 타이머와 "30초 뒤 재전송" 버튼에 쓴다.
 * 값은 {@code VerificationCodeService.CODE_TTL_SECONDS}·{@code RESEND_LIMIT_SECONDS} 상수다.
 */
public record RequestCodeResponse(
        int expiresInSeconds,
        int resendAfterSeconds
) {
}
