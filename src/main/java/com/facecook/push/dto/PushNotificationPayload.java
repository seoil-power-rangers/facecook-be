package com.facecook.push.dto;

public record PushNotificationPayload(
        String type,
        String title,
        String body,
        String url
) {
}
