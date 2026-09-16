package com.facecook.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordLoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
        @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {
}
