package com.facecook.profile.dto;

import jakarta.validation.constraints.NotBlank;

/** 업로드할 사진 형식({@code image/jpeg}, {@code image/png}, {@code image/webp}). 파일 자체는 서버로 오지 않는다. */
public record PhotoUploadUrlRequest(
        @NotBlank(message = "이미지 형식을 입력해주세요.")
        String contentType
) {
}
