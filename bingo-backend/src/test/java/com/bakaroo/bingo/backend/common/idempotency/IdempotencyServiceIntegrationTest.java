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