package com.facecook.superaccount.bootstrap;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserRole;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.support.EmailAddress;
import com.facecook.superaccount.config.SuperAccountProperties;
import com.facecook.common.time.EventTime;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;


/**
 * 서버가 뜰 때 슈퍼 계정을 만들거나 비밀번호를 맞춘다. 슈퍼 계정은 가입 API로 만들 수 없고 이 경로로만 생긴다.
 *
 * <p>{@code ApplicationRunner}: 애플리케이션 기동이 끝난 직후(로그 "Started FacecookBeApplication" 다음) 스프링이
 * {@link #run}을 한 번 부른다. 이때 웹 서버는 이미 열려 있다.</p>
 *
 * <p>동작({@code SuperAccountBootstrapTest}가 경우마다 확인):</p>
 * <ul>
 * <li>{@code SUPER_ACCOUNT_LOGIN}이나 {@code SUPER_ACCOUNT_PASSWORD}가 비었으면 아무것도 안 한다.</li>
 * <li>그 아이디의 계정이 없으면 {@code SUPER} 역할로 만든다(로그 "슈퍼 계정을 만들었습니다").</li>
 * <li>있고 역할이 {@code SUPER}면, 비밀번호가 환경변수와 다를 때만 해시를 바꾼다. 환경변수를 바꾸고 재시작하면 새 비밀번호가 된다.</li>
 * <li>있는데 다른 역할이면 건드리지 않고 오류 로그만 남긴다(참가자 계정을 슈퍼로 바꾸지 않는다).</li>
 * </ul>
 *
 * <p>서버 두 대가 동시에 떠서 둘 다 "없음"을 본 경우, {@code users.email}의 UNIQUE 제약 때문에 한쪽 저장이
 * {@code DataIntegrityViolationException}으로 실패한다. 다른 쪽이 이미 만들었으므로 무시한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAccountBootstrap implements ApplicationRunner {

    private final SuperAccountProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        String login = EmailAddress.normalize(properties.login());
        if (login.isBlank() || properties.password().isBlank()) {
            return;
        }

        User existing = userRepository.findByEmail(login).orElse(null);
        if (existing != null) {
            if (existing.getRole() != UserRole.SUPER) {
                log.error("슈퍼 계정 로그인 아이디가 이미 다른 역할의 계정에서 사용 중입니다. role={}", existing.getRole());
                return;
            }
            if (!passwordEncoder.matches(properties.password(), existing.getPasswordHash())) {
                existing.setPassword(passwordEncoder.encode(properties.password()));
                userRepository.save(existing);
                log.info("슈퍼 계정 비밀번호를 갱신했습니다.");
            }
            return;
        }

        try {
            User superUser = User.createSuper(login, EventTime.now(clock));
            superUser.setPassword(passwordEncoder.encode(properties.password()));
            userRepository.save(superUser);
            log.info("슈퍼 계정을 만들었습니다.");
        } catch (DataIntegrityViolationException ignored) {
            // 서버가 동시에 뜨면 한 쪽만 넣으면 된다.
        }
    }
}
