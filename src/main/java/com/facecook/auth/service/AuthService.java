package com.facecook.auth.service;

import com.facecook.auth.dto.AuthVerificationResponse;
import com.facecook.auth.dto.PasswordLoginRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 회원가입·로그인. 로그인 경로가 두 가지고, 대상 역할 범위가 서로
 * 다르다는 게 이 클래스에서 가장 헷갈리기 쉬운 지점이다.
 *
 * <ul>
 * <li>이메일 인증코드 로그인({@link #requestCode}/{@link #verifyLogin})
 * — {@code PARTICIPANT}만 가능({@link #findParticipant}가 role을
 * 걸러낸다).</li>
 * <li>비밀번호 로그인({@link #login}) — role 제한 없음. 참가자뿐 아니라
 * 관리자·슈퍼 계정도 이 경로로 로그인한다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<String> REQUIRED_TERMS = Set.of("service", "privacy");
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 이메일 인증코드를 발급하고 메일로 보낸다(회원가입 또는 로그인용,
     * {@code request.purpose()}로 구분).
     *
     * <p>전제조건: SIGNUP이면 미가입 이메일, LOGIN이면 이미 가입된
     * {@code PARTICIPANT}이고 정지 상태 아님(관리자·슈퍼 계정은 이 경로로
     * 로그인 자체가 불가능 — {@link #login} 참고).</p>
     *
     * <p>부작용: Redis에 6자리 코드를 5분 TTL로 저장하고 실제 메일을
     * 보낸다({@code VerificationCodeService#issue}). 30초 안에 재요청하면
     * {@code RESEND_TOO_SOON}으로 막힌다.</p>
     *
     * <p>예외: {@code ALREADY_REGISTERED}(SIGNUP인데 이미 가입),
     * {@code NOT_FOUND}(LOGIN인데 미가입), {@code SUSPENDED},
     * {@code RESEND_TOO_SOON}.</p>
     *
     * @see #verifySignup(VerifySignupRequest)
     * @see #verifyLogin(VerifyLoginRequest)
     */
    public RequestCodeResponse requestCode(RequestCodeRequest request) {
        String email = EmailAddress.normalize(request.email());
        validateRequestEligibility(email, request.purpose());
        verificationCodeService.issue(email, request.purpose());
        return new RequestCodeResponse(
                VerificationCodeService.CODE_TTL_SECONDS,
                VerificationCodeService.RESEND_LIMIT_SECONDS
        );
    }

    /**
     * 인증코드를 확인하고 참가자 계정을 생성한다(회원가입 완료).
     *
     * <p>전제조건: 필수 약관(service, privacy) 동의, 미가입 이메일,
     * {@link #requestCode}로 발급된 유효한 코드.</p>
     *
     * <p>부작용: {@code User}(role=PARTICIPANT)를 저장한다. 인증코드
     * 소비는 트랜잭션 커밋 이후로 미룬다({@link #consumeCodeAfterCommit})
     * — 가입 저장이 롤백되면 코드는 안 타서 같은 코드로 재시도할 수
     * 있다.</p>
     *
     * <p>예외: {@code TERMS_REQUIRED}, {@code ALREADY_REGISTERED}(동시
     * 가입 시도 시 DB unique 제약 위반도 이 코드로 변환됨), 인증코드
     * 불일치·만료 관련 예외({@code VerificationCodeService#verify}).</p>
     *
     * @see #requestCode(RequestCodeRequest)
     * @see #login(PasswordLoginRequest)
     */
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
            user = User.createParticipant(email, LocalDateTime.now());
            user.setPassword(passwordEncoder.encode(request.password()));
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.ALREADY_REGISTERED, exception);
        }

        consumeCodeAfterCommit(email, VerificationPurpose.SIGNUP);
        return AuthVerificationResponse.from(user);
    }

    /**
     * 인증코드로 로그인한다(비밀번호 불필요). {@code PARTICIPANT} 전용
     * — 관리자·슈퍼 계정은 이 경로로 로그인할 수 없다.
     *
     * <p>전제조건: 가입된 PARTICIPANT, 정지 상태 아님, {@link
     * #requestCode}로 발급된 유효한 코드.</p>
     *
     * <p>부작용: 인증코드를 소비한다(트랜잭션 커밋 후). 계정 자체는
     * 바뀌지 않는다.</p>
     *
     * <p>예외: {@code NOT_FOUND}(미가입 또는 참가자가 아님),
     * {@code SUSPENDED}.</p>
     *
     * @see #login(PasswordLoginRequest)
     */
    @Transactional(readOnly = true)
    public AuthVerificationResponse verifyLogin(VerifyLoginRequest request) {
        String email = EmailAddress.normalize(request.email());
        User user = findParticipant(email);
        validateActive(user);

        verificationCodeService.verify(email, request.code(), VerificationPurpose.LOGIN);
        consumeCodeAfterCommit(email, VerificationPurpose.LOGIN);
        return AuthVerificationResponse.from(user);
    }

    /**
     * 이메일+비밀번호로 로그인한다. role 제한이 없다 — 참가자·관리자·슈퍼
     * 계정 전부 이 API 하나로 로그인한다.
     *
     * <p>전제조건: 가입된 계정, 비밀번호 일치, 정지 상태 아님.</p>
     *
     * <p>부작용: 없음(순수 인증). 타이밍 공격 방지를 위해 이메일이
     * 존재하지 않거나 비밀번호 해시가 없는 경우에도 더미 해시({@link
     * #DUMMY_PASSWORD_HASH})로 {@code passwordEncoder.matches}를 한 번
     * 실행한다 — "이메일이 없어서 실패"와 "비밀번호가 틀려서 실패"의
     * 응답 시간 차이로 가입 여부가 유추되는 걸 막는다.</p>
     *
     * <p>예외: {@code INVALID_CREDENTIALS}(이메일 없음·비밀번호 불일치를
     * 구분 없이 같은 코드로 반환), {@code SUSPENDED}.</p>
     *
     * @see #verifyLogin(VerifyLoginRequest)
     */
    @Transactional(readOnly = true)
    public AuthVerificationResponse login(PasswordLoginRequest request) {
        String email = EmailAddress.normalize(request.email());
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        validateActive(user);

        String passwordHash = user.getPasswordHash();
        if (passwordHash == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), passwordHash)) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
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
