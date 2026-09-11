package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.entity.PushSubscription;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.Security;

@Component
@RequiredArgsConstructor
public class VapidWebPushGateway implements WebPushGateway {

    private final VapidProperties vapidProperties;
    private volatile PushService pushService;

    @Override
    public PushDeliveryResult send(PushSubscription subscription, String payload) throws Exception {
        PushService configuredPushService = pushService();
        Notification notification = new Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                payload
        );
        HttpResponse response = configuredPushService.send(notification, Encoding.AES128GCM);
        return new PushDeliveryResult(
                response.getStatusLine().getStatusCode(),
                response.getStatusLine().getReasonPhrase()
        );
    }

    private PushService pushService() throws GeneralSecurityException {
        PushService current = pushService;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (pushService == null) {
                validateProperties();
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                pushService = new PushService(vapidProperties.publicKey(), vapidProperties.privateKey());
            }
            return pushService;
        }
    }

    private void validateProperties() {
        if (vapidProperties.publicKey().isBlank() || vapidProperties.privateKey().isBlank()) {
            throw new IllegalStateException("VAPID 공개키와 비밀키가 설정되지 않았습니다.");
        }
    }
}
