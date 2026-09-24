package com.facecook.auth.dto;

import com.facecook.auth.entity.User;
import com.facecook.common.session.AuthenticatedUser;

import java.util.Locale;

/**
 * 가입·로그인 성공 응답이자 {@code GET /api/auth/me} 응답. FE는 {@code role}로
 * 참가자 화면/관리자 화면/슈퍼 화면 중 어디로 보낼지 정한다.
 *
 * <p>{@code role}은 DB 값({@code UserRole})을 소문자 문자열로 바꿔 보낸다
 * ({@code "participant"}, {@code "admin"}, {@code "super"}). 두 가지 {@code from}은
 * 만드는 재료만 다르다 — 로그인 직후엔 DB 엔티티({@code User}), {@code /me}에선
 * 세션에서 만든 {@code AuthenticatedUser}.</p>
 */
public record AuthVerificationResponse(
        Long userId,
        String email,
        String role
) {
    public static AuthVerificationResponse from(User user) {
        return new AuthVerificationResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name().toLowerCase(Locale.ROOT)
        );
    }

    public static AuthVerificationResponse from(AuthenticatedUser user) {
        return new AuthVerificationResponse(
                user.userId(),
                user.email(),
                user.role().name().toLowerCase(Locale.ROOT)
        );
    }
}
