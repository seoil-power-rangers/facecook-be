package com.facecook.auth.mail;

import com.facecook.auth.dto.VerificationPurpose;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.config.AuthMailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Brevo(SMTP)를 통해 인증코드 메일을 보내는 {@link AuthMailService}
 * 구현체.
 */
@Service
@RequiredArgsConstructor
public class BrevoAuthMailService implements AuthMailService {

    private final JavaMailSender mailSender;
    private final AuthMailProperties properties;

    /**
     * {@inheritDoc}
     *
     * <p>SMTP 전송 실패({@code MailException})는 {@code EMAIL_SEND_FAILED}
     * {@code ApiException}으로 변환해서 던진다.</p>
     */
    @Override
    public void sendVerificationCode(String email, String code, VerificationPurpose purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!properties.from().isBlank()) {
            message.setFrom(properties.from());
        }
        message.setTo(email);
        message.setSubject("[face 콕] 이메일 인증번호");
        message.setText(body(code, purpose));

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new ApiException(ErrorCode.EMAIL_SEND_FAILED, exception);
        }
    }

    private String body(String code, VerificationPurpose purpose) {
        String action = purpose == VerificationPurpose.SIGNUP ? "회원가입" : "로그인";
        return """
                face 콕 %s 인증번호입니다.

                인증번호: %s

                인증번호는 5분간 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(action, code);
    }
}
