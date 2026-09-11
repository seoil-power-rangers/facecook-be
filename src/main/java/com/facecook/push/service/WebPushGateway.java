package com.facecook.push.service;

import com.facecook.push.entity.PushSubscription;

public interface WebPushGateway {

    PushDeliveryResult send(PushSubscription subscription, String payload) throws Exception;
}
