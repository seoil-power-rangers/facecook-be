package com.facecook.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record PhotoUploadUrlRequest(
        @NotBlank(message = "이미지 형식을 입력해주세요.")
        String contentType
) {
}
