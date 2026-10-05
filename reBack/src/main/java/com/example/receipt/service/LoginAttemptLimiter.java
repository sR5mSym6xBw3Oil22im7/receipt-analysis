package com.example.receipt.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ユーザーIDごとにログイン失敗回数を数え、上限に達したら一定時間ログインを受け付けない。
 * 管理者パスワードの総当たりを遅らせるためのもので、状態はプロセス内メモリにのみ保持する。
 */
@Component
public class LoginAttemptLimiter {
    private static final int MAX_TRACKED_USERNAMES = 10_000;

    private final int maxFailures;
    private final Duration lockDuration;
    private final Clock clock;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptLimiter(@Value("${app.auth.login-max-failures:5}") int maxFailures,
                               @Value("${app.auth.login-lock-minutes:15}") long lockMinutes) {
        this(maxFailures, Duration.ofMinutes(lockMinutes), Clock.systemUTC());
    }

    LoginAttemptLimiter(int maxFailures, Duration lockDuration, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Attempt attempt = attempts.get(key(username));
        return attempt != null && attempt.lockedUntil != null && clock.instant().isBefore(attempt.lockedUntil);
    }

    public void recordFailure(String username) {
        Instant now = clock.instant();
        if (attempts.size() >= MAX_TRACKED_USERNAMES) {
            attempts.values().removeIf(attempt -> attempt.isExpired(now, lockDuration));
        }
        attempts.compute(key(username), (name, current) -> {
            Attempt next = current == null || current.isExpired(now, lockDuration) ? new Attempt() : current;
            next.failures++;
            next.lastFailure = now;
            if (next.failures >= maxFailures) {
                next.lockedUntil = now.plus(lockDuration);
                next.failures = 0;
            }
            return next;
        });
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempt {
        int failures;
        Instant lastFailure;
        Instant lockedUntil;

        // 最後の失敗とロックの両方から一定時間が経てば、失敗回数を数え直す
        boolean isExpired(Instant now, Duration window) {
            boolean lockOver = lockedUntil == null || !now.isBefore(lockedUntil);
            return lockOver && lastFailure != null && !now.isBefore(lastFailure.plus(window));
        }
    }
}
