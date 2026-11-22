package com.bakaroo.bingo.backend.feature.wallet.repository;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** Atomic, provider-scoped reservation of external withdrawal references. */
@Repository
public class WithdrawalTransferReferenceReservationRepository {

    private static final String WILDCARD_PROVIDER = "*";

    private final JdbcTemplate jdbcTemplate;

    public WithdrawalTransferReferenceReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reserve(String providerKey, String normalizedReference, Long withdrawalRequestId, Instant createdAt) {
        Optional<Reservation> existing = find(providerKey, normalizedReference);
        if (existing.isPresent()) {
            if ("AMBIGUOUS_LEGACY".equals(existing.get().status())) {
                throw new ConflictException("TRANSFER_REFERENCE_RECONCILIATION_REQUIRED",
                        "This reference matches ambiguous historical payout records and requires reconciliation");
            }
            throw referenceAlreadyUsed();
        }

        try {
            jdbcTemplate.update("""
                    insert into withdrawal_transfer_reference_reservations_v57
                        (provider_key, normalized_reference, reservation_status, source_type,
                         legacy_source_count, created_at)
                    values (?, ?, 'RESERVED', 'COMPLETION', 0, ?)
                    """, providerKey, normalizedReference, Timestamp.from(createdAt));
        } catch (DuplicateKeyException concurrentReservation) {
            // The pre-insert lookup is only an optimization; the unique key is the
            // concurrency authority. Translate the race into the public API code.
            throw referenceAlreadyUsed();
        }
        Long reservationId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        jdbcTemplate.update("""
                insert into withdrawal_transfer_reference_reservation_sources_v57
                    (reservation_id, withdrawal_request_id, source_type, created_at)
                values (?, ?, 'COMPLETION', ?)
                """, reservationId, withdrawalRequestId, Timestamp.from(createdAt));
    }

    private Optional<Reservation> find(String providerKey, String normalizedReference) {
        List<Reservation> exact = jdbcTemplate.query("""
                select id, reservation_status
                from withdrawal_transfer_reference_reservations_v57
                where provider_key = ? and normalized_reference = ?
                limit 1
                """, (rs, row) -> new Reservation(rs.getLong("id"), rs.getString("reservation_status")),
                providerKey, normalizedReference);
        if (!exact.isEmpty()) {
            return Optional.of(exact.get(0));
        }
        if (WILDCARD_PROVIDER.equals(providerKey)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                select id, reservation_status
                from withdrawal_transfer_reference_reservations_v57
                where provider_key = ? and normalized_reference = ?
                limit 1
                """, (rs, row) -> new Reservation(rs.getLong("id"), rs.getString("reservation_status")),
                WILDCARD_PROVIDER, normalizedReference).stream().findFirst();
    }

    private static ConflictException referenceAlreadyUsed() {
        return new ConflictException("TRANSFER_REFERENCE_ALREADY_USED",
                "This external transfer reference is already recorded for this payment provider");
    }

    private record Reservation(Long id, String status) { }
}
