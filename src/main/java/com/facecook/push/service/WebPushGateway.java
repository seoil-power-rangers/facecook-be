package com.facecook.push.service;

import com.facecook.push.entity.PushSubscription;

/**
 * 푸시 서버로 실제 HTTP 요청을 보내는 부분을 인터페이스로 분리했다. 구현은 {@code VapidWebPushGateway} 하나다.
 * {@code PushDeliveryService}는 이 인터페이스만 알아서, 단위 테스트에서는 가짜 구현으로 바꿔 네트워크 없이
 * "성공·만료·실패·제한 시간 초과" 경우를 확인한다({@code PushDeliveryServiceTest}).
 */
public interface WebPushGateway {

    PushDeliveryResult send(PushSubscription subscription, String payload) throws Exception;
}
