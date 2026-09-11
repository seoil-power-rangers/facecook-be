package com.facecook.push.service;

import com.facecook.push.config.VapidProperties;
import com.facecook.push.entity.PushSubscription;
import com.sun.net.httpserver.HttpServer;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VapidWebPushGatewayTest {

    private HttpServer pushServer;

    @AfterEach
    void tearDown() {
        if (pushServer != null) {
            pushServer.stop(0);
        }
    }

    @Test
    void encryptsPayloadAndCallsPushEndpointWithVapidAuthorization() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPair vapidKeyPair = keyPair();
        KeyPair subscriberKeyPair = keyPair();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentEncoding = new AtomicReference<>();
        AtomicInteger bodySize = new AtomicInteger();

        pushServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pushServer.createContext("/subscription", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentEncoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            bodySize.set(exchange.getRequestBody().readAllBytes().length);
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        pushServer.start();

        VapidProperties properties = new VapidProperties(
                encodePublicKey(vapidKeyPair),
                encodePrivateKey(vapidKeyPair)
        );
        VapidWebPushGateway gateway = new VapidWebPushGateway(properties);
        PushSubscription subscription = PushSubscription.create(
                2L,
                "http://127.0.0.1:" + pushServer.getAddress().getPort() + "/subscription",
                encodePublicKey(subscriberKeyPair),
                Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])
        );

        PushDeliveryResult result = gateway.send(subscription, "{\"title\":\"test\"}");

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(authorization.get()).startsWith("vapid t=");
        assertThat(contentEncoding.get()).isEqualTo("aes128gcm");
        assertThat(bodySize.get()).isPositive();
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
        return generator.generateKeyPair();
    }

    private String encodePublicKey(KeyPair keyPair) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Utils.encode((ECPublicKey) keyPair.getPublic()));
    }

    private String encodePrivateKey(KeyPair keyPair) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Utils.encode((ECPrivateKey) keyPair.getPrivate()));
    }
}
