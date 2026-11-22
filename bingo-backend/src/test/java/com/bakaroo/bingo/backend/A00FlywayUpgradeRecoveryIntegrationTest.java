package com.bakaroo.bingo.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Runs first and resets only bingo_qa_test before testing upgrades and recovery. */
class A00FlywayUpgradeRecoveryIntegrationTest extends MySqlIntegrationTest {

    @Test
    void upgradeFromV55ReservesEveryHistoricalReferenceAndFlagsAmbiguousLegacyRows() {
        TestDatabase testDatabase = requireIsolatedTestDatabase();
        DataSource dataSource = new DriverManagerDataSource(
                testDatabase.url(), testDatabase.username(), testDatabase.password());
        Flyway current = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .cleanDisabled(false)
                .load();
        current.clean();
        Flyway throughV55 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .target(MigrationVersion.fromVersion("55"))
                .load();
        throughV55.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into users (normalized_phone, display_name, password_hash, status, created_at, updated_at)
                values ('09119990001', 'Legacy withdrawal fixture', 'not-a-login-hash', 'ACTIVE', now(6), now(6))
                """);
        Long playerId = jdbc.queryForObject("select id from users where normalized_phone='09119990001'", Long.class);
        Long reviewerId = playerId;
        long first = insertCompletedWithdrawal(jdbc, playerId, reviewerId, "Legacy-Ref", "LEGACY-REF",
                "TELEBIRR", null, "TELEBIRR");
        long second = insertCompletedWithdrawal(jdbc, playerId, reviewerId, " legacy-ref ", null,
                "TELEBIRR", null, "TELEBIRR");
        long sameReferenceDifferentProvider = insertCompletedWithdrawal(jdbc, playerId, reviewerId,
                "LEGACY-REF", "LEGACY-REF", "BANK", "Example Bank", "EXAMPLE BANK");
        long unknownBankProvider = insertCompletedWithdrawal(jdbc, playerId, reviewerId,
                "Provider-Unknown", null, "BANK", "", "");
        long missingReference = insertCompletedWithdrawal(jdbc, playerId, reviewerId,
                null, null, "TELEBIRR", null, null);

        current.migrate();
        assertDoesNotThrow(current::validate);
        assertEquals("AMBIGUOUS_LEGACY", jdbc.queryForObject("""
                select reservation_status from withdrawal_transfer_reference_reservations
                where provider_key='TELEBIRR' and normalized_reference='LEGACY-REF'
                """, String.class));
        Long telebirrReservation = jdbc.queryForObject("""
                select id from withdrawal_transfer_reference_reservations
                where provider_key='TELEBIRR' and normalized_reference='LEGACY-REF'
                """, Long.class);
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from withdrawal_transfer_reference_reservation_sources
                where reservation_id=? and withdrawal_request_id in (?, ?)
                """, Integer.class, telebirrReservation, first, second));
        assertEquals("RESERVED", jdbc.queryForObject("""
                select reservation_status from withdrawal_transfer_reference_reservations
                where provider_key='EXAMPLE BANK' and normalized_reference='LEGACY-REF'
                """, String.class));
        assertEquals("AMBIGUOUS_LEGACY", jdbc.queryForObject("""
                select reservation_status from withdrawal_transfer_reference_reservations
                where provider_key='*' and normalized_reference='PROVIDER-UNKNOWN'
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from withdrawal_transfer_reference_reservation_sources
                where withdrawal_request_id=?
                """, Integer.class, unknownBankProvider));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from withdrawal_transfer_reference_reservation_sources
                where withdrawal_request_id=?
                """, Integer.class, sameReferenceDifferentProvider));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from withdrawal_transfer_reference_migration_issues
                where withdrawal_request_id=? and issue_code='COMPLETED_WITHOUT_REFERENCE'
                """, Integer.class, missingReference));
        assertEquals(" legacy-ref ", jdbc.queryForObject(
                "select transfer_reference from wallet_withdrawal_requests where id=?", String.class, second));
        assertEquals(5, jdbc.queryForObject(
                "select count(*) from wallet_withdrawal_requests where status='COMPLETED'", Integer.class));
    }

    private static long insertCompletedWithdrawal(JdbcTemplate jdbc, Long playerId, Long reviewerId,
            String reference, String normalizedReference, String destinationType, String bankName,
            String providerKey) {
        jdbc.update("""
                insert into wallet_withdrawal_requests
                    (player_id, operation_id, amount, currency, destination_type, account_name,
                     phone_number, bank_name, account_number, status, created_at, reviewed_by,
                     reviewed_at, transfer_reference, review_note, normalized_transfer_reference,
                     transfer_provider_key, payment_evidence)
                values (?, ?, 25.00, 'ETB', ?, 'Legacy recipient', ?, ?, ?, 'COMPLETED', now(6), ?,
                        now(6), ?, 'Historical completion', ?, ?, null)
                """, playerId, UUID.randomUUID().toString(), destinationType,
                "TELEBIRR".equals(destinationType) ? "251911000101" : null,
                bankName, "BANK".equals(destinationType) ? "legacy-account" : null,
                reviewerId, reference, normalizedReference, providerKey);
        return jdbc.queryForObject("select last_insert_id()", Long.class);
    }

    @Test
    void initializesFreshSchemaUpgradesFromV6AndRepairsDetectedChecksumDrift() {
        TestDatabase testDatabase = requireIsolatedTestDatabase();
        DataSource dataSource = new DriverManagerDataSource(
                testDatabase.url(), testDatabase.username(), testDatabase.password());
        Flyway current = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .cleanDisabled(false)
                .load();

        current.clean();
        MigrationVersion latestVersion = Arrays.stream(current.info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .max(MigrationVersion::compareTo)
                .orElseThrow();
        assertEquals(current.info().pending().length, current.migrate().migrationsExecuted);
        assertEquals(latestVersion, current.info().current().getVersion());
        assertDoesNotThrow(current::validate);

        current.clean();
        Flyway throughV6 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .target(MigrationVersion.fromVersion("6"))
                .load();
        throughV6.migrate();
        assertEquals("6", throughV6.info().current().getVersion().getVersion());

        current.migrate();
        assertEquals(latestVersion, current.info().current().getVersion());
        assertDoesNotThrow(current::validate);

        // A later deployment of the same migrations must leave the schema history unchanged.
        assertEquals(0, current.migrate().migrationsExecuted);
        assertEquals(latestVersion, current.info().current().getVersion());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer originalChecksum = jdbcTemplate.queryForObject(
                "select checksum from flyway_schema_history where version = '7'", Integer.class);
        assertNotNull(originalChecksum);
        jdbcTemplate.update("update flyway_schema_history set checksum = checksum + 1 where version = '7'");
        assertThrows(FlywayException.class, current::validate);
        current.repair();
        assertEquals(originalChecksum, jdbcTemplate.queryForObject(
                "select checksum from flyway_schema_history where version = '7'", Integer.class));
        assertDoesNotThrow(current::validate);
    }
}
