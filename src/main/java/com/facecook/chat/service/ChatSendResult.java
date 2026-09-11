package com.facecook.chat.service;

import com.facecook.chat.dto.ChatMessageResponse;

public record ChatSendResult(ChatMessageResponse message, boolean created) {
}
