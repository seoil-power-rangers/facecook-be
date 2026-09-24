package com.facecook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 브라우저가 우리 API를 호출해도 되는 프론트엔드 주소 목록
 * ({@code CORS_ALLOWED_ORIGINS}). REST({@link WebConfig})와 WebSocket
 * 핸드셰이크({@link WebSocketConfig}) 양쪽에서 같은 목록을 쓴다.
 *
 * <p>브라우저는 다른 주소(예: Vercel의 FE → EC2의 BE)로 쿠키를 실어 보낼 때,
 * 서버가 그 주소를 명시적으로 허용해야만 응답을 FE 코드에 넘겨준다.</p>
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of("http://localhost:3000", "http://localhost:3001")
                : List.copyOf(allowedOrigins);
    }
}
