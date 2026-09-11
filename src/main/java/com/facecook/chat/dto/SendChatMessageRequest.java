package com.facecook.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendChatMessageRequest(
        @NotBlank(message = "메시지 내용을 입력해주세요.")
        @Size(max = 1000, message = "메시지는 1000자를 넘을 수 없습니다.")
        String content,

        @NotNull(message = "clientMessageId는 필수입니다.")
        UUID clientMessageId
) {
}
