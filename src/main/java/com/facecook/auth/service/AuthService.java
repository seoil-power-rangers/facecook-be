package com.facecook.auth.service;

import com.facecook.auth.dto.AuthVerificationResponse;
import com.facecook.auth.dto.RequestCodeRequest;
import com.facecook.auth.dto.RequestCodeResponse;
import com.facecook.auth.dto.VerificationPurpose;
import com.facecook.auth.dto.VerifyLoginRequest;
import com.facecook.auth.dto.VerifySignupRequest;
import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserRole;
import com.facecook.auth.entity.UserStatus;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.support.EmailAddress;
import com.facecook.common.exception.ApiException;
import com.facecook.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<String> REQUIRED_TERMS = Set.of("service", "privacy");

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;

    public RequestCodeResponse requestCode(RequestCodeRequest request) {
        String email = EmailAddress.normalize(request.email());
        validateRequestEligibility(email, request.purpose());
        verificationCodeService.issue(email, request.purpose());
        return new RequestCodeResponse(
                VerificationCodeService.CODE_TTL_SECONDS,
                VerificationCodeService.RESEND_LIMIT_SECONDS
        );
    }

    @Transactional
    public AuthVerificationResponse verifySignup(VerifySignupRequest request) {
        String email = EmailAddress.normalize(request.email());
        validateRequiredTerms(request.agreedTerms());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.ALREADY_REGISTERED);
        }

        verificationCodeService.verify(email, request.code(), VerificationPurpose.SIGNUP);

        User user;
        try {
            user = userRepository.saveAndFlush(User.createParticipant(email, LocalDateTime.now()));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.ALREADY_REGISTERED, exception);
        }

        consumeCodeAfterCommit(email, VerificationPurpose.SIGNUP);
        return AuthVerificationResponse.from(user);
    }

    @Transactional(readOnly = true)
    public AuthVerificationResponse verifyLogin(VerifyLoginRequest request) {
        String email = EmailAddress.normalize(request.email());
        User user = findParticipant(email);
        validateActive(user);

        verificationCodeService.verify(email, request.code(), VerificationPurpose.LOGIN);
        consumeCodeAfterCommit(email, VerificationPurpose.LOGIN);
        return AuthVerificationResponse.from(user);
    }

    private void validateRequestEligibility(String email, VerificationPurpose purpose) {
        if (purpose == VerificationPurpose.SIGNUP) {
            if (userRepository.existsByEmail(email)) {
                throw new ApiException(ErrorCode.ALREADY_REGISTERED);
            }
            return;
        }

        User user = findParticipant(email);
        validateActive(user);
    }

    private User findParticipant(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> user.getRole() == UserRole.PARTICIPANT)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "가입되지 않은 이메일입니다."));
    }

    private void validateActive(User user) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ApiException(ErrorCode.SUSPENDED);
        }
    }

    private void validateRequiredTerms(Set<String> agreedTerms) {
        if (!agreedTerms.containsAll(REQUIRED_TERMS)) {
            throw new ApiException(ErrorCode.TERMS_REQUIRED);
        }
    }

    private void consumeCodeAfterCommit(String email, VerificationPurpose purpose) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            verificationCodeService.consume(email, purpose);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                verificationCodeService.consume(email, purpose);
            }
        });
    }
}
