package com.facecook.auth.service;

import com.facecook.auth.dto.AuthVerificationResponse;
import com.facecook.auth.dto.PasswordLoginRequest;
import com.facecook.auth.dto.RequestCodeRequest;
import com.facecook.auth.dto.VerificationPurpose;
import com.facecook.auth.dto.VerifyLoginRequest;
import com.facecook.auth.dto.VerifySignupRequest;
import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserRole;
import com.facecook.auth.repository.UserRepository;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationCodeService verificationCodeService;

    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(userRepository, verificationCodeService, passwordEncoder);
    }

    @Test
    void requestsSignupCodeWithNormalizedEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);

        authService.requestCode(new RequestCodeRequest(" User@Example.com ", VerificationPurpose.SIGNUP));

        verify(verificationCodeService).issue("user@example.com", VerificationPurpose.SIGNUP);
    }

    @Test
    void rejectsSignupCodeRequestForRegisteredEmail() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertErrorCode(
                () -> authService.requestCode(
                        new RequestCodeRequest("user@example.com", VerificationPurpose.SIGNUP)
                ),
                ErrorCode.ALREADY_REGISTERED
        );
        verify(verificationCodeService, never()).issue(any(), any());
    }

    @Test
    void rejectsLoginCodeRequestForSuspendedParticipant() {
        User user = participant("user@example.com");
        user.suspend();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertErrorCode(
                () -> authService.requestCode(
                        new RequestCodeRequest("user@example.com", VerificationPurpose.LOGIN)
                ),
                ErrorCode.SUSPENDED
        );
        verify(verificationCodeService, never()).issue(any(), any());
    }

    @Test
    void rejectsSignupWithoutAllRequiredTerms() {
        VerifySignupRequest request = new VerifySignupRequest(
                "user@example.com",
                "123456",
                "password123",
                Set.of("service")
        );

        assertErrorCode(() -> authService.verifySignup(request), ErrorCode.TERMS_REQUIRED);
        verify(verificationCodeService, never()).verify(any(), any(), any());
    }

    @Test
    void createsParticipantAfterSuccessfulSignupVerification() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthVerificationResponse response = authService.verifySignup(new VerifySignupRequest(
                " User@Example.com ",
                "123456",
                "password123",
                Set.of("service", "privacy", "photo")
        ));

        verify(verificationCodeService)
                .verify("user@example.com", "123456", VerificationPurpose.SIGNUP);
        verify(verificationCodeService).consume("user@example.com", VerificationPurpose.SIGNUP);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.role()).isEqualTo("participant");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", savedUser.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void verifiesActiveParticipantLogin() {
        User user = participant("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        AuthVerificationResponse response = authService.verifyLogin(
                new VerifyLoginRequest("USER@example.com", "654321")
        );

        verify(verificationCodeService)
                .verify("user@example.com", "654321", VerificationPurpose.LOGIN);
        verify(verificationCodeService).consume("user@example.com", VerificationPurpose.LOGIN);
        assertThat(response.email()).isEqualTo("user@example.com");
    }

    @Test
    void rejectsLoginForUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertErrorCode(
                () -> authService.verifyLogin(new VerifyLoginRequest("missing@example.com", "123456")),
                ErrorCode.NOT_FOUND
        );
    }

    @Test
    void logsInActiveParticipantWithPassword() {
        User user = participantWithPassword("user@example.com", "password123");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        AuthVerificationResponse response = authService.login(
                new PasswordLoginRequest(" USER@example.com ", "password123")
        );

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.role()).isEqualTo("participant");
    }

    @Test
    void logsInAdminWithPassword() {
        User admin = participantWithPassword("admin@example.com", "password123");
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        AuthVerificationResponse response = authService.login(
                new PasswordLoginRequest("admin@example.com", "password123")
        );

        assertThat(response.role()).isEqualTo("admin");
    }

    @Test
    void logsInSuperWithPassword() {
        User superUser = participantWithPassword("rhgustjrwkwlxjf", "dlwlalsqhwlxjf");
        ReflectionTestUtils.setField(superUser, "role", UserRole.SUPER);
        when(userRepository.findByEmail("rhgustjrwkwlxjf")).thenReturn(Optional.of(superUser));

        AuthVerificationResponse response = authService.login(
                new PasswordLoginRequest("rhgustjrwkwlxjf", "dlwlalsqhwlxjf")
        );

        assertThat(response.role()).isEqualTo("super");
        assertThat(response.email()).isEqualTo("rhgustjrwkwlxjf");
    }

    @Test
    void rejectsPasswordLoginForUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertErrorCode(
                () -> authService.login(new PasswordLoginRequest("missing@example.com", "password123")),
                ErrorCode.INVALID_CREDENTIALS
        );
    }

    @Test
    void rejectsPasswordLoginForWrongPassword() {
        User user = participantWithPassword("user@example.com", "password123");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertErrorCode(
                () -> authService.login(new PasswordLoginRequest("user@example.com", "wrong-password")),
                ErrorCode.INVALID_CREDENTIALS
        );
    }

    @Test
    void rejectsPasswordLoginForLegacyAccountWithoutPassword() {
        User user = participant("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertErrorCode(
                () -> authService.login(new PasswordLoginRequest("user@example.com", "password123")),
                ErrorCode.INVALID_CREDENTIALS
        );
    }

    @Test
    void rejectsPasswordLoginForSuspendedParticipant() {
        User user = participantWithPassword("user@example.com", "password123");
        user.suspend();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertErrorCode(
                () -> authService.login(new PasswordLoginRequest("user@example.com", "password123")),
                ErrorCode.SUSPENDED
        );
    }

    private User participant(String email) {
        return User.createParticipant(email, LocalDateTime.now());
    }

    private User participantWithPassword(String email, String password) {
        User user = participant(email);
        user.setPassword(passwordEncoder.encode(password));
        return user;
    }

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected)
                );
    }
}
