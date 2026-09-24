package com.facecook.push.dto;

/**
 * 푸시 알림 내용. JSON으로 암호화돼 브라우저로 가고, FE 서비스 워커({@code public/push-sw.js})가 {@code title}·{@code body}로
 * 알림을 띄운 뒤 누르면 {@code url}로 이동한다. {@code type}은 종류 구분값(COOK_RECEIVED / MATCH_CREATED /
 * CHAT_MESSAGE_RECEIVED)이다. 내용은 {@code ParticipantPushNotificationService}가 정한다.
 */
public record PushNotificationPayload(
        String type,
        String title,
        String body,
        String url
) {
}
