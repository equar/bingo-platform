package com.bakaroo.bingo.backend.common.idempotency;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    IdempotencyService idempotencyService;

    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private User firstActor;
    private User secondActor;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
        firstActor = userRepository.save(createActor());
        secondActor = userRepository.save(createActor());
    }

    @AfterEach
    void cleanUp() {
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll(List.of(firstActor, secondActor));
    }

    @Test
    void idempotencyIsScopedToTheActor() {
        var first = idempotencyService.execute(firstActor.getId(), "shared-key", "hash-a", "op-a", 201, String.class,
                () -> "alpha");
        var second = idempotencyService.execute(secondActor.getId(), "shared-key", "hash-b", "op-b", 201, String.class,
                () -> "beta");
        var replay = idempotencyService.execute(firstActor.getId(), "shared-key", "hash-a", "op-c", 201, String.class,
                () -> "gamma");

        assertEquals("alpha", first.body());
        assertEquals("beta", second.body());
        assertEquals("alpha", replay.body());
        assertEquals(2L, idempotencyRecordRepository.count());

        assertThrows(ConflictException.class, () -> idempotencyService.execute(firstActor.getId(), "shared-key",
                "hash-x", "op-d", 201, String.class, () -> "delta"));
    }

    @Test
    void nullAndUnknownActorsAreRejectedByServiceAndDatabaseConstraints() {
        assertThrows(NullPointerException.class, () -> idempotencyService.execute(null, "null-actor", "hash",
                "op-null", 200, String.class, () -> "never"));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertRecord(null, "database-null-actor"));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertRecord(Long.MAX_VALUE, "unknown-actor"));
        assertEquals(0L, idempotencyRecordRepository.count());
    }

    @Test
    void databaseRejectsDuplicateActorKeysButAllowsTheKeyForAnotherActor() {
        insertRecord(firstActor.getId(), "same-key");
        insertRecord(secondActor.getId(), "same-key");

        assertThrows(DataIntegrityViolationException.class,
                () -> insertRecord(firstActor.getId(), "same-key"));
        assertEquals(2L, idempotencyRecordRepository.count());
    }

    @Test
    void concurrentSameActorRetriesExecuteTheMutationOnlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch firstMutationEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstMutation = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        try {
            Future<IdempotentResult<String>> first = executor.submit(() -> {
                bothStarted.countDown();
                return idempotencyService.execute(firstActor.getId(), "concurrent-key", "same-hash", "op-one",
                        201, String.class, () -> {
                            executions.incrementAndGet();
                            firstMutationEntered.countDown();
                            await(releaseFirstMutation);
                            return "created";
                        });
            });
            org.junit.jupiter.api.Assertions.assertTrue(firstMutationEntered.await(5, TimeUnit.SECONDS));
            Future<IdempotentResult<String>> second = executor.submit(() -> {
                bothStarted.countDown();
                return idempotencyService.execute(firstActor.getId(), "concurrent-key", "same-hash", "op-two",
                        201, String.class, () -> {
                            executions.incrementAndGet();
                            return "duplicate";
                        });
            });

            org.junit.jupiter.api.Assertions.assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
            // Allow the first transaction to commit; the second is serialized by
            // the existing actor-row lock and then replays its response.
            releaseFirstMutation.countDown();
            assertEquals("created", first.get(10, TimeUnit.SECONDS).body());
            assertEquals("created", second.get(10, TimeUnit.SECONDS).body());
            assertEquals(1, executions.get());
            assertEquals(1L, idempotencyRecordRepository.count());
        } finally {
            releaseFirstMutation.countDown();
            executor.shutdownNow();
        }
    }

    private void insertRecord(Long actorId, String key) {
        jdbcTemplate.update("""
                insert into idempotency_records
                    (actor_id, idempotency_key, request_hash, response_status, response_body,
                     operation_id, created_at, updated_at)
                values (?, ?, 'hash', 200, '{}', 'operation', current_timestamp(6), current_timestamp(6))
                """, actorId, key);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release concurrent idempotency mutation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent idempotency mutation interrupted", exception);
        }
    }

    private User createActor() {
        String phone = String.format(Locale.ROOT, "2519%08d", new java.security.SecureRandom().nextInt(100_000_000));
        return User.create(
                phone,
                "Idempotency Test Actor",
                "encoded-test-password",
                AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER),
                Instant.now());
    }
}
