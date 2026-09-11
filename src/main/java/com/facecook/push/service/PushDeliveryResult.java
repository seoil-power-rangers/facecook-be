package com.facecook.push.service;

public record PushDeliveryResult(int statusCode, String reason) {

    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean subscriptionExpired() {
        return statusCode == 404 || statusCode == 410;
    }
}
