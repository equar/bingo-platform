package com.equar.bingo.backend.feature.auth.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuthenticationAttemptBucketStore {

    private static final Duration BUCKET_RETENTION = Duration.ofHours(48);
    private static final AtomicLong writesSinceCleanup = new AtomicLong();

    private final JdbcTemplate jdbcTemplate;

    public AuthenticationAttemptBucketStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordAttempt(String key, int limit, Duration window, Duration lockout) {
        Integer acquired = jdbcTemplate.queryForObject("select get_lock(?, 10)", Integer.class, key);
        if (!Integer.valueOf(1).equals(acquired)) {
            throw new IllegalStateException("Could not acquire the shared authentication-attempt lock");
        }
        try {
            return recordAttemptUnderLock(key, limit, window, lockout);
        } finally {
            Integer released = jdbcTemplate.queryForObject("select release_lock(?)", Integer.class, key);
            if (!Integer.valueOf(1).equals(released)) {
                throw new IllegalStateException("Could not release the shared authentication-attempt lock");
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isAllowed(String key, int limit, Duration window, Duration lockout) {
        Integer acquired = jdbcTemplate.queryForObject("select get_lock(?, 10)", Integer.class, key);
        if (!Integer.valueOf(1).equals(acquired)) {
            throw new IllegalStateException("Could not acquire the shared authentication-attempt lock");
        }
        try {
            return isAllowedUnderLock(key, limit, window, lockout);
        } finally {
            Integer released = jdbcTemplate.queryForObject("select release_lock(?)", Integer.class, key);
            if (!Integer.valueOf(1).equals(released)) {
                throw new IllegalStateException("Could not release the shared authentication-attempt lock");
            }
        }
    }

    private boolean recordAttemptUnderLock(String key, int limit, Duration window, Duration lockout) {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update("""
                insert ignore into authentication_attempt_buckets
                    (bucket_key, attempt_count, window_started_at, locked_until, last_attempt_at)
                values (?, 0, ?, null, ?)
                """, key, timestamp, timestamp);

        Bucket bucket = jdbcTemplate.queryForObject("""
                select attempt_count, window_started_at, locked_until
                from authentication_attempt_buckets
                where bucket_key = ?
                for update
                """, (resultSet, rowNumber) -> {
            Timestamp lockedUntil = resultSet.getTimestamp("locked_until");
            return new Bucket(
                    resultSet.getInt("attempt_count"),
                    resultSet.getTimestamp("window_started_at").toInstant(),
                    lockedUntil == null ? null : lockedUntil.toInstant());
        }, key);

        int attempts = bucket.attemptCount();
        Instant windowStartedAt = bucket.windowStartedAt();
        if ((bucket.lockedUntil() != null && !bucket.lockedUntil().isAfter(now))
                || !windowStartedAt.plus(window).isAfter(now)) {
            attempts = 0;
            windowStartedAt = now;
        } else if (bucket.lockedUntil() != null) {
            return false;
        }

        if (attempts >= limit) {
            jdbcTemplate.update("""
                    update authentication_attempt_buckets
                    set locked_until = ?, last_attempt_at = ?
                    where bucket_key = ?
                    """, Timestamp.from(now.plus(lockout)), timestamp, key);
            cleanupPeriodically(now);
            return false;
        }

        jdbcTemplate.update("""
                update authentication_attempt_buckets
                set attempt_count = ?, window_started_at = ?, locked_until = null, last_attempt_at = ?
                where bucket_key = ?
                """, attempts + 1, Timestamp.from(windowStartedAt), timestamp, key);
        cleanupPeriodically(now);
        return true;
    }

    private boolean isAllowedUnderLock(String key, int limit, Duration window, Duration lockout) {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        jdbcTemplate.update("""
                insert ignore into authentication_attempt_buckets
                    (bucket_key, attempt_count, window_started_at, locked_until, last_attempt_at)
                values (?, 0, ?, null, ?)
                """, key, timestamp, timestamp);

        Bucket bucket = jdbcTemplate.queryForObject("""
                select attempt_count, window_started_at, locked_until
                from authentication_attempt_buckets
                where bucket_key = ?
                for update
                """, (resultSet, rowNumber) -> {
            Timestamp lockedUntil = resultSet.getTimestamp("locked_until");
            return new Bucket(
                    resultSet.getInt("attempt_count"),
                    resultSet.getTimestamp("window_started_at").toInstant(),
                    lockedUntil == null ? null : lockedUntil.toInstant());
        }, key);

        if ((bucket.lockedUntil() != null && !bucket.lockedUntil().isAfter(now))
                || !bucket.windowStartedAt().plus(window).isAfter(now)) {
            jdbcTemplate.update("""
                    update authentication_attempt_buckets
                    set attempt_count = 0, window_started_at = ?, locked_until = null, last_attempt_at = ?
                    where bucket_key = ?
                    """, timestamp, timestamp, key);
            return true;
        }
        if (bucket.lockedUntil() != null) {
            return false;
        }
        if (bucket.attemptCount() >= limit) {
            jdbcTemplate.update("""
                    update authentication_attempt_buckets
                    set locked_until = ?, last_attempt_at = ?
                    where bucket_key = ?
                    """, Timestamp.from(now.plus(lockout)), timestamp, key);
            cleanupPeriodically(now);
            return false;
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(String key) {
        jdbcTemplate.update("delete from authentication_attempt_buckets where bucket_key = ?", key);
    }

    private void cleanupPeriodically(Instant now) {
        if (writesSinceCleanup.incrementAndGet() % 100 == 0) {
            jdbcTemplate.update("delete from authentication_attempt_buckets where last_attempt_at < ?",
                    Timestamp.from(now.minus(BUCKET_RETENTION)));
        }
    }

    private record Bucket(int attemptCount, Instant windowStartedAt, Instant lockedUntil) {
    }
}
