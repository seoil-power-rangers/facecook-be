package com.facecook.push.service;

/**
 * 푸시 서버의 응답 결과. 2xx면 성공, 404·410이면 그 구독이 더 이상 유효하지 않다(브라우저가 알림 권한을 끊는 등) —
 * {@code PushDeliveryService}가 그 구독을 지운다. 그 밖은 실패로 센다.
 */
public record PushDeliveryResult(int statusCode, String reason) {

    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean subscriptionExpired() {
        return statusCode == 404 || statusCode == 410;
    }
}
