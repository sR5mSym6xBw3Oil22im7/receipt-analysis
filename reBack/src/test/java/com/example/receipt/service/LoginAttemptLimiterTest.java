package com.example.receipt.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {
    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(15), clock);

    @Test
    void locksAfterMaxFailuresAndUnlocksAfterLockDuration() {
        limiter.recordFailure("admin");
        limiter.recordFailure("admin");
        assertThat(limiter.isLocked("admin")).isFalse();

        limiter.recordFailure("admin");
        assertThat(limiter.isLocked("admin")).isTrue();
        assertThat(limiter.isLocked("other")).isFalse();

        clock.advance(Duration.ofMinutes(15));
        assertThat(limiter.isLocked("admin")).isFalse();
    }

    @Test
    void successResetsFailureCount() {
        limiter.recordFailure("admin");
        limiter.recordFailure("admin");
        limiter.recordSuccess("admin");
        limiter.recordFailure("admin");
        limiter.recordFailure("admin");

        assertThat(limiter.isLocked("admin")).isFalse();
    }

    @Test
    void oldFailuresAreForgottenAfterWindow() {
        limiter.recordFailure("admin");
        limiter.recordFailure("admin");
        clock.advance(Duration.ofMinutes(15));
        limiter.recordFailure("admin");

        assertThat(limiter.isLocked("admin")).isFalse();
    }

    @Test
    void usernameIsComparedIgnoringCaseAndSurroundingSpaces() {
        limiter.recordFailure("Admin");
        limiter.recordFailure(" admin ");
        limiter.recordFailure("ADMIN");

        assertThat(limiter.isLocked("admin")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
