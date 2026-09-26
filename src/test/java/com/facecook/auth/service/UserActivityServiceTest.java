package com.facecook.auth.service;

import com.facecook.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

    private static final ZoneId EVENT_ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-09-30T03:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Test
    void touchesWithADebounceWindowSoRapidPollingDoesNotWriteEveryTime() {
        UserActivityService service = new UserActivityService(userRepository, Clock.fixed(NOW, ZoneOffset.UTC));

        service.touch(7L);

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> staleBeforeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).touchLastActiveAt(org.mockito.ArgumentMatchers.eq(7L), nowCaptor.capture(), staleBeforeCaptor.capture());

        LocalDateTime expectedNow = LocalDateTime.ofInstant(NOW, EVENT_ZONE);
        assertThat(nowCaptor.getValue()).isEqualTo(expectedNow);
        // staleBefore가 now보다 과거여야 "이미 최근에 갱신됐으면 쓰지 않는다"는 디바운스가 실제로 걸린다.
        assertThat(staleBeforeCaptor.getValue()).isBefore(expectedNow);
        assertThat(Duration.between(staleBeforeCaptor.getValue(), expectedNow)).isPositive();
    }

    @Test
    void skipsTheDatabaseWithinTheDebounceWindowOnThisServer() {
        MutableClock clock = new MutableClock(NOW);
        UserActivityService service = new UserActivityService(userRepository, clock);

        service.touch(7L);
        clock.advance(Duration.ofSeconds(29));
        service.touch(7L);

        verify(userRepository, times(1)).touchLastActiveAt(eq(7L), any(), any());
    }

    @Test
    void sendsAgainOnceTheDebounceWindowHasPassed() {
        MutableClock clock = new MutableClock(NOW);
        UserActivityService service = new UserActivityService(userRepository, clock);

        service.touch(7L);
        clock.advance(Duration.ofSeconds(30));
        service.touch(7L);

        verify(userRepository, times(2)).touchLastActiveAt(eq(7L), any(), any());
    }

    @Test
    void remembersEachUserSeparately() {
        MutableClock clock = new MutableClock(NOW);
        UserActivityService service = new UserActivityService(userRepository, clock);

        service.touch(7L);
        service.touch(8L);
        service.touch(7L);

        verify(userRepository, times(1)).touchLastActiveAt(eq(7L), any(), any());
        verify(userRepository, times(1)).touchLastActiveAt(eq(8L), any(), any());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
