package com.facecook.auth.mail;

import com.facecook.auth.dto.VerificationPurpose;

/**
 * 인증코드 메일 발송. 구현체를 갈아끼울 수 있게 인터페이스로 분리했다
 * (지금은 {@link BrevoAuthMailService} 하나뿐).
 */
public interface AuthMailService {

    /**
     * email로 인증코드를 담은 메일을 보낸다.
     *
     * <p>전제조건: 없음.</p>
     *
     * <p>부작용: 실제 메일이 발송된다.</p>
     *
     * <p>예외: {@code EMAIL_SEND_FAILED}(발송 실패 — 구현체 참고).</p>
     */
    void sendVerificationCode(String email, String code, VerificationPurpose purpose);
}
