package com.facecook.chat.service;

import com.facecook.chat.dto.ChatMessageResponse;

/**
 * 전송 결과. {@code created}가 false면 같은 clientMessageId로 이미 저장된 메시지를 다시 돌려준 것(재전송)이라,
 * 호출부({@code ChatMessageController})는 다시 발행하지 않고 {@code ChatService}도 푸시를 다시 보내지 않는다.
 */
public record ChatSendResult(ChatMessageResponse message, boolean created) {
}
