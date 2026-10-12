package com.equar.bingo.backend.common.idempotency;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.feature.wallet.entity.IdempotencyRecord;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository, ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    public <T> IdempotentResult<T> execute(Long actorId,
            String idempotencyKey,
            String requestHash,
            String operationId,
            int successStatus,
            Class<T> responseType,
            Supplier<T> supplier) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        String lockKey = actorId + ":" + idempotencyKey;
        ReentrantLock lock = locks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return executeWithinLock(actorId, idempotencyKey, requestHash, operationId, successStatus, responseType,
                    supplier);
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                locks.remove(lockKey, lock);
            }
        }
    }

    @Transactional
    protected <T> IdempotentResult<T> executeWithinLock(Long actorId,
            String idempotencyKey,
            String requestHash,
            String operationId,
            int successStatus,
            Class<T> responseType,
            Supplier<T> supplier) {
        IdempotencyRecord existing = idempotencyRecordRepository.findByActorIdAndIdempotencyKey(actorId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "That Idempotency-Key was already used for a different request");
            }

            return new IdempotentResult<>(existing.getResponseStatus(),
                    readValue(existing.getResponseBody(), responseType));
        }

        T response = supplier.get();
        idempotencyRecordRepository.save(IdempotencyRecord.create(
                actorId,
                idempotencyKey,
                requestHash,
                successStatus,
                writeValue(response),
                operationId != null ? operationId : UUID.randomUUID().toString(),
                Instant.now()));
        return new IdempotentResult<>(successStatus, response);
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new UncheckedIOException(
                    new java.io.IOException("Unable to serialize idempotent response", exception));
        }
    }

    private <T> T readValue(String value, Class<T> responseType) {
        try {
            return objectMapper.readValue(value, responseType);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to deserialize idempotent response", exception);
        }
    }
}