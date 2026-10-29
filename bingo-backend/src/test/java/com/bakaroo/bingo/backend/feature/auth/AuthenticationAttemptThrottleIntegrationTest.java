package com.bakaroo.bingo.backend.feature.auth;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.auth.service.AuthenticationAttemptBucketStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationAttemptThrottleIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    AuthenticationAttemptBucketStore bucketStore;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void concurrentAttemptsShareTheDatabaseLimit() throws Exception {
        jdbcTemplate.update("delete from authentication_attempt_buckets");
        String key = "a".repeat(64);
        int requestCount = 24;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            List<java.util.concurrent.Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return bucketStore.recordAttempt(key, 5, Duration.ofMinutes(1), Duration.ofSeconds(5));
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            long allowed = 0;
            for (var result : results) {
                if (result.get(20, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertEquals(5, allowed);
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from authentication_attempt_buckets where bucket_key = ?",
                Integer.class,
                key));
    }
}
