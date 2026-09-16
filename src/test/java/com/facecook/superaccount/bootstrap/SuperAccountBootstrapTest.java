package com.facecook.superaccount.bootstrap;

import com.facecook.auth.entity.User;
import com.facecook.auth.entity.UserRole;
import com.facecook.auth.repository.UserRepository;
import com.facecook.superaccount.config.SuperAccountProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAccountBootstrapTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void createsMissingSuperAccount() {
        when(userRepository.findByEmail("rhgustjrwkwlxjf")).thenReturn(Optional.empty());
        SuperAccountBootstrap bootstrap = new SuperAccountBootstrap(
                new SuperAccountProperties("rhgustjrwkwlxjf", "dlwlalsqhwlxjf"),
                userRepository,
                passwordEncoder
        );

        bootstrap.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.SUPER);
        assertThat(saved.getEmail()).isEqualTo("rhgustjrwkwlxjf");
        assertThat(passwordEncoder.matches("dlwlalsqhwlxjf", saved.getPasswordHash())).isTrue();
    }

    @Test
    void doesNotOverwriteExistingAccount() {
        User existing = User.createSuper("rhgustjrwkwlxjf", java.time.LocalDateTime.now());
        existing.setPassword("already-hashed");
        when(userRepository.findByEmail("rhgustjrwkwlxjf")).thenReturn(Optional.of(existing));
        SuperAccountBootstrap bootstrap = new SuperAccountBootstrap(
                new SuperAccountProperties("rhgustjrwkwlxjf", "dlwlalsqhwlxjf"),
                userRepository,
                passwordEncoder
        );

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }
}
