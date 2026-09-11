package com.facecook.chat.controller;

import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.dto.SendChatMessageRequest;
import com.facecook.chat.redis.ChatMessagePublisher;
import com.facecook.chat.service.ChatSendResult;
import com.facecook.chat.service.ChatService;
import com.facecook.chat.websocket.ChatPrincipal;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatService chatService;
    private final ChatMessagePublisher messagePublisher;

    @MessageMapping("/chat/{matchId}/send")
    @SendToUser(destinations = "/queue/chat-acks", broadcast = false)
    public ChatMessageResponse send(
            @DestinationVariable Long matchId,
            @Payload @Valid SendChatMessageRequest request,
            Principal principal
    ) {
        if (!(principal instanceof ChatPrincipal chatPrincipal)) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        ChatSendResult result = chatService.send(chatPrincipal.user().userId(), matchId, request);
        if (result.created()) {
            messagePublisher.publish(result.message());
        }
        return result.message();
    }
}
