package com.facecook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/login} 요청 본문. 이메일 형식 검사({@code @Email})를 일부러
 * 하지 않는다 — 슈퍼 계정 로그인 아이디도 이 필드로 받는다.
 */
public record PasswordLoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
