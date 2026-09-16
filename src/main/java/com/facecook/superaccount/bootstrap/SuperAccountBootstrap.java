package com.facecook.superaccount.bootstrap;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserRole;
import com.facecook.auth.repository.UserRepository;
import com.facecook.auth.support.EmailAddress;
import com.facecook.superaccount.config.SuperAccountProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAccountBootstrap implements ApplicationRunner {

    private final SuperAccountProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
            User superUser = User.createSuper(login, LocalDateTime.now());
            superUser.setPassword(passwordEncoder.encode(properties.password()));
            userRepository.save(superUser);
            log.info("슈퍼 계정을 만들었습니다.");
        } catch (DataIntegrityViolationException ignored) {
            // 서버가 동시에 뜨면 한 쪽만 넣으면 된다.
        }
    }
}
