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

import java.time.ZoneOffset;
import java.time.Instant;
import java.time.Clock;
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

    // UTC 03:00 = 한국 시간 12:00.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void doesNotCreateSuperAccountWhenCredentialsAreBlank() {
        SuperAccountBootstrap bootstrap = new SuperAccountBootstrap(
                new SuperAccountProperties("", ""),
                userRepository,
                passwordEncoder,
                CLOCK
        );

        bootstrap.run(null);

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsMissingSuperAccount() {
        when(userRepository.findByEmail("rhgustjrwkwlxjf")).thenReturn(Optional.empty());
        SuperAccountBootstrap bootstrap = new SuperAccountBootstrap(
                new SuperAccountProperties("rhgustjrwkwlxjf", "dlwlalsqhwlxjf"),
                userRepository,
                passwordEncoder,
                CLOCK
        );

        bootstrap.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(UserRole.SUPER);
        assertThat(saved.getEmail()).isEqualTo("rhgustjrwkwlxjf");
        assertThat(saved.getCreatedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 9, 30, 12, 0));
        assertThat(passwordEncoder.matches("dlwlalsqhwlxjf", saved.getPasswordHash())).isTrue();
    }

    @Test
    void updatesPasswordWhenExistingSuperPasswordDiffers() {
        User existing = User.createSuper("rhgustjrwkwlxjf", java.time.LocalDateTime.now());
        existing.setPassword("already-hashed");
        when(userRepository.findByEmail("rhgustjrwkwlxjf")).thenReturn(Optional.of(existing));
        SuperAccountBootstrap bootstrap = new SuperAccountBootstrap(
                new SuperAccountProperties("rhgustjrwkwlxjf", "dlwlalsqhwlxjf"),
                userRepository,
                passwordEncoder,
                CLOCK
        );

        bootstrap.run(null);

        verify(userRepository).save(existing);
        assertThat(passwordEncoder.matches("dlwlalsqhwlxjf", existing.getPasswordHash())).isTrue();
    }

    @Test
    void doesNotSaveWhenExistingSuperPasswordMatches() {
        User existing = User.createSuper("rhgustjrwkwlxjf", java.time.LocalDateTime.now());
        existing.setPassword(passwordEncoder.encode("dlwlalsqhwlxjf"));
        when(userRepository.findByEmail("rhgustjrwkwlxjf")).thenReturn(Optional.of(existing));
        SuperAccountBootstrap bootstrap = new SuperAccountBootstrap(
                new SuperAccountProperties("rhgustjrwkwlxjf", "dlwlalsqhwlxjf"),
                userRepository,
                passwordEncoder,
                CLOCK
        );

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNotOverwriteExistingNonSuperAccount() {
        User existing = User.createParticipant("rhgustjrwkwlxjf", java.time.LocalDateTime.now());
        existing.setPassword(passwordEncoder.encode("participant-password"));
        String originalPasswordHash = existing.getPasswordHash();
        when(userRepository.findByEmail("rhgustjrwkwlxjf")).thenReturn(Optional.of(existing));
        SuperAccountBootstrap bootstrap = new SuperAccountBootstrap(
                new SuperAccountProperties("rhgustjrwkwlxjf", "dlwlalsqhwlxjf"),
                userRepository,
                passwordEncoder,
                CLOCK
        );

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
        assertThat(existing.getRole()).isEqualTo(UserRole.PARTICIPANT);
        assertThat(existing.getPasswordHash()).isEqualTo(originalPasswordHash);
    }
}
