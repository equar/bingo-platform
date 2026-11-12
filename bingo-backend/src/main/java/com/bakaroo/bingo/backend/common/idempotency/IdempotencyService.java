package com.bakaroo.bingo.backend.common.idempotency;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.entity.IdempotencyRecord;
import com.bakaroo.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRecordRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public <T> IdempotentResult<T> execute(Long actorId,
            String idempotencyKey,
            String requestHash,
            String operationId,
            int successStatus,
            Class<T> responseType,
            Supplier<T> supplier) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        userRepository.findByIdForUpdate(actorId)
                .orElseThrow(() -> new IllegalStateException("Idempotency actor does not exist"));

        IdempotencyRecord existing = idempotencyRecordRepository
                .findByActorIdAndIdempotencyKeyForUpdate(actorId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                log.atWarn()
                        .addKeyValue("operation", "idempotency_check")
                        .addKeyValue("outcome", "key_reused_with_different_request")
                        .addKeyValue("actorId", actorId)
                        .log("Idempotency key rejected for a different request");
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "That Idempotency-Key was already used for a different request");
            }

            log.atInfo()
                    .addKeyValue("operation", "idempotency_check")
                    .addKeyValue("outcome", "replay")
                    .addKeyValue("actorId", actorId)
                    .addKeyValue("status", existing.getResponseStatus())
                    .log("Idempotent request replayed");
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
