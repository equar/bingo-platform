package com.equar.bingo.backend.common.idempotency;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class IdempotencyServiceIntegrationTest {

    @Autowired
    IdempotencyService idempotencyService;

    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
    }

    @Test
    void idempotencyIsScopedToTheActor() {
        var first = idempotencyService.execute(101L, "shared-key", "hash-a", "op-a", 201, String.class,
                () -> "alpha");
        var second = idempotencyService.execute(202L, "shared-key", "hash-b", "op-b", 201, String.class,
                () -> "beta");
        var replay = idempotencyService.execute(101L, "shared-key", "hash-a", "op-c", 201, String.class,
                () -> "gamma");

        assertEquals("alpha", first.body());
        assertEquals("beta", second.body());
        assertEquals("alpha", replay.body());
        assertEquals(2L, idempotencyRecordRepository.count());

        assertThrows(ConflictException.class, () -> idempotencyService.execute(101L, "shared-key", "hash-x",
                "op-d", 201, String.class, () -> "delta"));
    }
}