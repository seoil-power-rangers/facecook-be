package com.facecook.common.session;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

/**
 * 7일 유효 세션을 HMAC-SHA256으로 서명한 문자열 토큰으로 만든다.
 * 토큰엔 userId·만료시각만 담고, 역할·정지여부는 항상 DB에서 다시 조회한다
 * (계정 상태가 바뀌어도 즉시 반영되게 하기 위함 — 토큰 자체를 신뢰 소스로 두지 않음).
 */
@Component
@RequiredArgsConstructor
public class SessionTokenSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SessionProperties properties;
    private final Clock clock;

    /**
     * userId의 세션 토큰을 만든다. 형식은
     * {@code base64url("userId:만료초") + "." + base64url(HMAC-SHA256 서명)}.
     *
     * <p>서명 키({@code SESSION_SECRET})를 모르면 같은 서명을 만들 수 없어서,
     * 사용자가 토큰 안의 userId를 바꾸면 {@link #verify}에서 걸린다. 내용 자체는
     * 암호화가 아니라 인코딩이라 누구나 읽을 수 있다 — 그래서 비밀 정보는 넣지 않는다.</p>
     *
     * <p>호출: {@code SessionCookieService#issue}(로그인·가입 성공 시).</p>
     */
    public String issue(Long userId, long ttlSeconds) {
        long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
        String payload = userId + ":" + expiresAt;
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = ENCODER.encodeToString(sign(encodedPayload));
        return encodedPayload + "." + signature;
    }

    /**
     * 토큰의 서명과 만료를 확인한다. 형식이 틀리거나 서명이 안 맞거나 만료됐으면
     * 이유를 가리지 않고 {@link Optional#empty()}를 돌려준다(호출부는 전부 "로그인 안 됨"으로 본다).
     *
     * <p>서명 비교에 {@code equals} 대신 {@link MessageDigest#isEqual}을 쓴다 —
     * 앞자리가 몇 개 맞는지에 따라 비교 시간이 달라지지 않게 해서, 응답 시간으로
     * 서명을 추측하는 공격을 막는다.</p>
     *
     * <p>{@code Optional}을 돌려주는 이유: "없음"을 {@code null} 대신 타입으로
     * 드러내서, 호출부가 비어 있는 경우를 잊지 않고 처리하게 한다.</p>
     */
    public Optional<SessionToken> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        int separatorIndex = token.indexOf('.');
        if (separatorIndex < 0) {
            return Optional.empty();
        }

        String encodedPayload = token.substring(0, separatorIndex);
        String signature = token.substring(separatorIndex + 1);

        byte[] expectedSignature = sign(encodedPayload);
        byte[] actualSignature;
        try {
            actualSignature = DECODER.decode(signature);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            return Optional.empty();
        }

        return parsePayload(encodedPayload);
    }

    private Optional<SessionToken> parsePayload(String encodedPayload) {
        String payload;
        try {
            payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        int separatorIndex = payload.indexOf(':');
        if (separatorIndex < 0) {
            return Optional.empty();
        }

        try {
            Long userId = Long.parseLong(payload.substring(0, separatorIndex));
            long expiresAt = Long.parseLong(payload.substring(separatorIndex + 1));
            SessionToken token = new SessionToken(userId, expiresAt);
            if (token.isExpired(clock.instant().getEpochSecond())) {
                return Optional.empty();
            }
            return Optional.of(token);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private byte[] sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("세션 서명에 실패했습니다.", exception);
        }
    }
}
