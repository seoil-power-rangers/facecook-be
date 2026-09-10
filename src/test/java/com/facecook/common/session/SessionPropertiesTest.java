package com.facecook.common.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionPropertiesTest {

    @Test
    void rejectsBlankSecret() {
        // 세션 서명 비밀키는 절대 코드/설정 파일에 안전한 기본값을 두지 않는다 —
        // 배포 시 SESSION_SECRET을 빼먹으면 "조용히 부팅"이 아니라 즉시 실패해야
        // 알려진 문자열로 세션이 위조되는 사고를 막을 수 있다.
        assertThatThrownBy(() -> new SessionProperties("", "FACECOOK_SESSION", 604800, false, "Lax"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SessionProperties(null, "FACECOOK_SESSION", 604800, false, "Lax"))
                .isInstanceOf(IllegalStateException.class);
    }
}
