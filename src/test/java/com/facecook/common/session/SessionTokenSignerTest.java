package com.facecook.common.session;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenSignerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final SessionProperties properties =
            new SessionProperties("test-secret", "FACECOOK_SESSION", 604800, false, "Lax", null);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final SessionTokenSigner signer = new SessionTokenSigner(properties, clock);

    @Test
    void issuedTokenVerifiesBackToSameUser() {
        String token = signer.issue(42L, 3600);

        Optional<SessionToken> verified = signer.verify(token);

        assertThat(verified).isPresent();
        assertThat(verified.get().userId()).isEqualTo(42L);
    }

    @Test
    void rejectsTamperedPayload() {
        String token = signer.issue(1L, 3600);
        String[] parts = token.split("\\.", 2);
        String tampered = parts[0] + "AAAA" + "." + parts[1];

        assertThat(signer.verify(tampered)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        SessionTokenSigner otherSigner = new SessionTokenSigner(
                new SessionProperties("different-secret", "FACECOOK_SESSION", 604800, false, "Lax", null),
                clock
        );
        String token = otherSigner.issue(1L, 3600);

        assertThat(signer.verify(token)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        String token = signer.issue(1L, -1);

        assertThat(signer.verify(token)).isEmpty();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(signer.verify("not-a-valid-token")).isEmpty();
        assertThat(signer.verify("")).isEmpty();
        assertThat(signer.verify(null)).isEmpty();
    }
}
