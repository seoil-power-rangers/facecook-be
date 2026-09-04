package com.facecook.auth.mail;

import com.facecook.auth.dto.VerificationPurpose;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import com.facecook.config.AuthMailProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrevoAuthMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsSignupVerificationMail() {
        BrevoAuthMailService service = new BrevoAuthMailService(
                mailSender,
                new AuthMailProperties("sender@example.com")
        );

        service.sendVerificationCode("user@example.com", "123456", VerificationPurpose.SIGNUP);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("sender@example.com");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getText()).contains("회원가입", "123456", "5분");
    }

    @Test
    void mapsMailFailureToApiError() {
        BrevoAuthMailService service = new BrevoAuthMailService(
                mailSender,
                new AuthMailProperties("sender@example.com")
        );
        doThrow(new MailSendException("failed")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.sendVerificationCode(
                "user@example.com",
                "123456",
                VerificationPurpose.LOGIN
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_SEND_FAILED)
        );
    }
}
