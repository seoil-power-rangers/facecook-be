package com.facecook.chat.websocket;

/**
 * WebSocket 세션 속성 키. 핸드셰이크 때 {@link ChatHandshakeInterceptor}가 넣고, 이후 프레임마다
 * {@link ChatInboundChannelInterceptor}가 토큰을 꺼내 다시 검증한다.
 */
public final class ChatSessionAttributes {

    public static final String SESSION_TOKEN = "chatSessionToken";
    public static final String AUTHENTICATED_USER = "chatAuthenticatedUser";

    private ChatSessionAttributes() {
    }
}
