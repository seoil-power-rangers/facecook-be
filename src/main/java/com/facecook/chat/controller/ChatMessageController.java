package com.facecook.chat.controller;

import com.facecook.chat.dto.ChatMessageResponse;
import com.facecook.chat.dto.SendChatMessageRequest;
import com.facecook.chat.redis.ChatMessagePublisher;
import com.facecook.chat.service.ChatSendResult;
import com.facecook.chat.service.ChatService;
import com.facecook.chat.websocket.ChatPrincipal;
import com.facecook.chat.websocket.ChatStompErrorHandler;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * {@code @MessageMapping} 핸들러 안에서 던진 예외는 {@link ChatStompErrorHandler}가
 * 상속한 프로토콜 레벨 오류 처리기를 거치지 않는다(별도 스레드에서 실행돼서
 * StompSubProtocolHandler의 동기 try/catch를 못 탄다) — 그래서 여기서 직접
 * ERROR frame을 만들어 clientOutboundChannel로 보낸다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatService chatService;
    private final ChatMessagePublisher messagePublisher;
    private final ChatStompErrorHandler stompErrorHandler;

    @Qualifier("clientOutboundChannel")
    private final MessageChannel clientOutboundChannel;

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
            publishBestEffort(result.message());
        }
        return result.message();
    }

    /**
     * 발행 실패(Redis 장애 등)는 저장이 끝난 메시지를 실시간으로만 못 보내는 것이지
     * 저장 자체가 실패한 게 아니다. 여기서 던지면 발신자에게 ERROR frame이 가서
     * 이미 저장된 메시지를 다시 보내게 되는데, 그러면 같은 clientMessageId라
     * ACK만 오고 재발행되지 않는다(facecook-be#84). 그래서 발행 실패는 로그만
     * 남기고 ACK는 저장 성공을 기준으로 그대로 반환한다.
     */
    private void publishBestEffort(ChatMessageResponse message) {
        try {
            messagePublisher.publish(message);
        } catch (RuntimeException publishFailure) {
            log.error("채팅 메시지 실시간 발행에 실패했습니다. messageId={}", message.messageId(), publishFailure);
        }
    }

    @MessageExceptionHandler(Exception.class)
    public void handleException(
            Exception exception,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId
    ) {
        if (!(exception instanceof ApiException)) {
            log.error("채팅 STOMP 처리 중 예상하지 못한 예외가 발생했습니다. sessionId={}", sessionId, exception);
        }
        Message<byte[]> errorFrame = stompErrorHandler.buildErrorFrame(exception, sessionId);
        try {
            clientOutboundChannel.send(errorFrame);
        } catch (MessagingException sendFailure) {
            log.warn("채팅 STOMP ERROR frame 전송에 실패했습니다. sessionId={}", sessionId, sendFailure);
        }
    }
}
