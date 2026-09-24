package com.facecook.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 채팅 전송 본문(STOMP SEND의 JSON). {@code clientMessageId}는 같은 메시지를 다시 보낼 때도 그대로 써야 한다 —
 * 서버는 이 값으로 중복 저장을 막는다({@code ChatService#send}).
 *
 * <p>검증: 컨트롤러 파라미터의 {@code @Valid}가 검사하고, 실패하면 ERROR 프레임(400 {@code VALIDATION})이 간다.</p>
 */
public record SendChatMessageRequest(
        @NotBlank(message = "메시지 내용을 입력해주세요.")
        @Size(max = 1000, message = "메시지는 1000자를 넘을 수 없습니다.")
        String content,

        @NotNull(message = "clientMessageId는 필수입니다.")
        UUID clientMessageId
) {
}
