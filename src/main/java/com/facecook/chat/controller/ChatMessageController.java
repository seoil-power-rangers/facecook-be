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
 * WebSocket(STOMP)으로 들어오는 채팅 전송을 받는다. REST의 {@code @RestController}에 해당한다.
 *
 * <p>전송 한 번의 흐름:</p>
 * <ol>
 * <li>FE가 {@code SEND /app/chat/{matchId}/send}를 보냄 → {@code ChatInboundChannelInterceptor}가 세션 재확인·
 * 목적지·당사자 검사</li>
 * <li>{@link #send} → {@code ChatService#send}가 운영시간·당사자 확인 후 저장(같은 clientMessageId면 기존 것)</li>
 * <li>새로 저장됐으면 Redis 채널로 발행({@code ChatMessagePublisher}) → 모든 서버의
 * {@code ChatMessageSubscriber}가 받아 {@code /topic/chat/{matchId}} 구독자에게 전달</li>
 * <li>{@link #send}의 반환값은 {@code @SendToUser("/queue/chat-acks")}로 보낸 사람의 이 연결에만 돌아간다(ACK).
 * FE는 이 ACK로 "전송 중…"을 "보냄"으로 바꾼다</li>
 * </ol>
 *
 * <p>{@code @MessageMapping}의 경로 앞 {@code /app}은 {@code WebSocketConfig}의 애플리케이션 접두사라 생략된다.</p>
 *
 * <p>{@code @MessageMapping} 핸들러 안에서 던진 예외는 {@link ChatStompErrorHandler}가
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

    /**
     * 채팅 메시지 하나를 저장하고 발행한다. 반환값이 곧 보낸 사람에게 가는 ACK다.
     *
     * <p>{@code principal}은 연결 때 만든 {@code ChatPrincipal}이다({@code ChatHandshakeHandler}). 재전송(같은
     * clientMessageId)이면 다시 발행하지 않고 기존 메시지를 ACK로만 돌려준다 — 상대는 이미 받았거나, 못 받았으면
     * FE의 이력 대조로 채운다.</p>
     *
     * <p>예외는 아래 {@link #handleException}이 받아 보낸 사람에게 ERROR 프레임으로 알린다.</p>
     */
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

    /** 이 컨트롤러의 {@code @MessageMapping}에서 난 예외를 그 세션에 ERROR 프레임으로 보낸다(REST의 GlobalExceptionHandler 역할). */
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
