package com.facecook.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 푸시 VAPID 키 쌍({@code VAPID_PUBLIC_KEY}, {@code VAPID_PRIVATE_KEY}). 공개키는 FE가 브라우저 구독을 만들 때
 * 쓰고({@code GET /api/push/vapid-public-key}), 비밀키는 서버가 푸시 요청에 서명할 때 쓴다. 푸시 서버(FCM 등)는
 * 이 서명으로 "구독을 만든 그 서버가 보낸 요청"인지 확인한다. 비어 있으면 서버는 뜨지만 발송은 실패한다(로그만).
 */
@ConfigurationProperties(prefix = "app.push.vapid")
public record VapidProperties(String publicKey, String privateKey) {

    public VapidProperties {
        publicKey = publicKey == null ? "" : publicKey;
        privateKey = privateKey == null ? "" : privateKey;
    }
}
