package com.bakaroo.bingo.backend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/** Runs first and resets only bingo_qa_test before testing upgrades and recovery. */
class A00FlywayUpgradeRecoveryIntegrationTest extends MySqlIntegrationTest {

    @AfterAll
    static void leaveOnlyTheGuardedDisposableSchemaClean() {
        TestDatabase testDatabase = requireIsolatedTestDatabase();
        Flyway.configure()
                .dataSource(new DriverManagerDataSource(
                        testDatabase.url(), testDatabase.username(), testDatabase.password()))
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .cleanDisabled(false)
                .load()
                .clean();
    }

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
                insert into users (normalized_phone, display_name, password_hash, status, referral_code,
                                   created_at, updated_at)
                values ('09119990001', 'Legacy withdrawal fixture', 'not-a-login-hash', 'ACTIVE', 'LEG001',
                        now(6), now(6))
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
                select reservation_status from withdrawal_transfer_reference_reservations_v57
                where provider_key='TELEBIRR' and normalized_reference='LEGACYREF'
                """, String.class));
        Long telebirrReservation = jdbc.queryForObject("""
                select id from withdrawal_transfer_reference_reservations_v57
                where provider_key='TELEBIRR' and normalized_reference='LEGACYREF'
                """, Long.class);
        assertEquals(2, jdbc.queryForObject("""
                select count(*) from withdrawal_transfer_reference_reservation_sources_v57
                where reservation_id=? and withdrawal_request_id in (?, ?)
                """, Integer.class, telebirrReservation, first, second));
        assertEquals("RESERVED", jdbc.queryForObject("""
                select reservation_status from withdrawal_transfer_reference_reservations_v57
                where provider_key='EXAMPLE BANK' and normalized_reference='LEGACYREF'
                """, String.class));
        assertEquals("AMBIGUOUS_LEGACY", jdbc.queryForObject("""
                select reservation_status from withdrawal_transfer_reference_reservations_v57
                where provider_key='*' and normalized_reference='PROVIDERUNKNOWN'
                """, String.class));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from withdrawal_transfer_reference_reservation_sources_v57
                where withdrawal_request_id=?
                """, Integer.class, unknownBankProvider));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from withdrawal_transfer_reference_reservation_sources_v57
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

    @Test
    void upgradesPopulatedV13SchemaThroughLatestWithoutLosingActorsOrPayoutRows() {
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
        Flyway throughV13 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .target(MigrationVersion.fromVersion("13"))
                .load();
        throughV13.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String phone = String.format(java.util.Locale.ROOT, "09%09d",
                new java.security.SecureRandom().nextInt(1_000_000_000));
        jdbc.update("""
                insert into users (normalized_phone, display_name, password_hash, status,
                                   created_at, updated_at)
                values (?, 'V13 migration actor', 'not-a-login-hash', 'ACTIVE', now(6), now(6))
                """, phone);
        Long userId = jdbc.queryForObject(
                "select id from users where normalized_phone=?", Long.class, phone);
        String actorOperationId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into idempotency_records
                    (actor_id, idempotency_key, request_hash, response_status, response_body,
                     operation_id, created_at, updated_at)
                values (?, ?, 'v13-hash', 200, '{}', ?, now(6), now(6))
                """, userId, "v13-key-" + suffix, actorOperationId);

        jdbc.update("""
                insert into bingo_agents
                    (user_id, status, city, territory, id_document_type, id_document_number,
                     created_at, updated_at, version)
                values (?, 'ACTIVE', 'Test City', 'Test Territory', 'TEST', ?, now(6), now(6), 0)
                """, userId, "DOC-" + suffix);
        Long agentId = jdbc.queryForObject(
                "select id from bingo_agents where user_id=?", Long.class, userId);

        String roomName = "V13 migration " + suffix;
        jdbc.update("""
                insert into bingo_rooms
                    (name, ticket_price, currency, timezone, schedule_rule, session_capacity,
                     max_tickets_per_player, ticket_sales_close_on_session_start,
                     allow_multiple_winners_per_prize, active, configured_patterns_json,
                     configured_prizes_json, created_at, updated_at)
                values (?, 1.00, 'ETB', 'UTC', 'manual', 10, 1, true, false, true,
                        '{}', '{}', now(6), now(6))
                """, roomName);
        Long roomId = jdbc.queryForObject(
                "select id from bingo_rooms where name=?", Long.class, roomName);
        String sessionName = "V13 migration session " + suffix;
        jdbc.update("""
                insert into bingo_sessions
                    (room_id, session_name, state, scheduled_start_at, ticket_price_snapshot,
                     currency_snapshot, session_capacity_snapshot, max_tickets_per_player_snapshot,
                     ticket_sales_close_on_session_start, allow_multiple_winners_per_prize,
                     patterns_snapshot_json, prizes_snapshot_json, created_at, updated_at)
                values (?, ?, 'SCHEDULED', now(6), 1.00, 'ETB', 10, 1, true, false,
                        '{}', '{}', now(6), now(6))
                """, roomId, sessionName);
        Long sessionId = jdbc.queryForObject(
                "select id from bingo_sessions where session_name=?", Long.class, sessionName);
        String ticketCode = "V13" + suffix.toUpperCase(java.util.Locale.ROOT);
        String ticketOperationId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into bingo_tickets
                    (session_id, owner_id, public_ticket_code, operation_id, grid_json, purchased_at)
                values (?, ?, ?, ?, '{}', now(6))
                """, sessionId, userId, ticketCode, ticketOperationId);
        Long ticketId = jdbc.queryForObject(
                "select id from bingo_tickets where public_ticket_code=?", Long.class, ticketCode);
        String claimOperationId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into bingo_win_claims
                    (ticket_id, session_id, claimant_id, claimed_pattern, status,
                     server_validation_passed, draw_sequence_snapshot, called_numbers_snapshot_json,
                     evidence_json, operation_id, created_at, updated_at)
                values (?, ?, ?, 'LINE', 'PENDING', true, 0, '[]', '{}', ?, now(6), now(6))
                """, ticketId, sessionId, userId, claimOperationId);
        Long claimId = jdbc.queryForObject(
                "select id from bingo_win_claims where operation_id=?", Long.class, claimOperationId);
        String entitlementOperationId = UUID.randomUUID().toString();
        String payoutCode = "PAY-" + suffix.toUpperCase(java.util.Locale.ROOT);
        jdbc.update("""
                insert into bingo_prize_entitlements
                    (win_claim_id, session_id, ticket_id, claimant_id, prize_type, settlement_path,
                     status, prize_amount, currency, operation_id, created_at, payout_code)
                values (?, ?, ?, ?, 'LINE', 'WALLET', 'APPROVED', 1.00, 'ETB', ?, now(6), ?)
                """, claimId, sessionId, ticketId, userId, entitlementOperationId, payoutCode);
        Long entitlementId = jdbc.queryForObject(
                "select id from bingo_prize_entitlements where payout_code=?", Long.class, payoutCode);
        String payoutOperationId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into payout_verification_records
                    (entitlement_id, agent_id, payout_code, status, disbursed_amount,
                     created_at, updated_at, operation_id)
                values (?, ?, ?, 'PENDING', 1.00, now(6), now(6), ?)
                """, entitlementId, agentId, payoutCode, payoutOperationId);

        current.migrate();
        assertDoesNotThrow(current::validate);
        assertEquals("57", current.info().current().getVersion().getVersion());
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from idempotency_records
                where actor_id=? and idempotency_key=? and operation_id=?
                """, Integer.class, userId, "v13-key-" + suffix, actorOperationId));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from payout_verification_records
                where entitlement_id=? and operation_id=? and payout_code=?
                """, Integer.class, entitlementId, payoutOperationId, payoutCode));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from idempotency_records record
                left join users actor on actor.id=record.actor_id
                where record.actor_id is null or actor.id is null
                """, Integer.class));
    }

    @Test
    void v55RejectsNullAndOrphanActorsWithoutDeletingLegacyRecords() {
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
        Flyway throughV54 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .target(MigrationVersion.fromVersion("54"))
                .load();
        throughV54.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                insert into idempotency_records
                    (actor_id, idempotency_key, request_hash, response_status, response_body,
                     operation_id, created_at, updated_at)
                values (null, 'legacy-null-actor', 'hash-null', 200, '{}', ?, now(6), now(6))
                """, UUID.randomUUID().toString());
        jdbc.update("""
                insert into idempotency_records
                    (actor_id, idempotency_key, request_hash, response_status, response_body,
                     operation_id, created_at, updated_at)
                values (9223372036854775807, 'legacy-orphan-actor', 'hash-orphan', 200, '{}', ?, now(6), now(6))
                """, UUID.randomUUID().toString());

        assertThrows(FlywayException.class, current::migrate);
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from idempotency_records where idempotency_key like 'legacy-%'", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from idempotency_records where actor_id is null", Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from idempotency_records record
                left join users actor on actor.id=record.actor_id
                where record.actor_id is not null and actor.id is null
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema=database() and table_name='idempotency_records'
                  and column_name='actor_id' and is_nullable='YES'
                """, Integer.class));
    }

    @Test
    void guardedMySqlDumpRestorePreservesCurrentMigrationStateAndRows() throws Exception {
        TestDatabase testDatabase = requireIsolatedTestDatabase();
        DataSource dataSource = new DriverManagerDataSource(
                testDatabase.url(), testDatabase.username(), testDatabase.password());
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(DATABASE)
                .schemas(DATABASE)
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String phone = String.format(java.util.Locale.ROOT, "09%09d",
                new java.security.SecureRandom().nextInt(1_000_000_000));
        jdbc.update("""
                insert into users (normalized_phone, display_name, password_hash, status, referral_code,
                                   created_at, updated_at)
                values (?, 'Dump restore fixture', 'not-a-login-hash', 'ACTIVE', ?, now(6), now(6))
                """, phone, "DR" + suffix.toUpperCase(java.util.Locale.ROOT));
        Long userId = jdbc.queryForObject(
                "select id from users where normalized_phone=?", Long.class, phone);
        String idempotencyKey = "dump-restore-" + suffix;
        String operationId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into idempotency_records
                    (actor_id, idempotency_key, request_hash, response_status, response_body,
                     operation_id, created_at, updated_at)
                values (?, ?, 'dump-hash', 200, '{}', ?, now(6), now(6))
                """, userId, idempotencyKey, operationId);
        Map<String, Integer> before = dumpRestoreCounts(jdbc);

        Path dump = Files.createTempFile("bingo-mysql8-dump-restore-", ".sql");
        try {
            URI mysqlUrl = URI.create(testDatabase.url().substring("jdbc:".length()));
            String host = mysqlUrl.getHost();
            String port = Integer.toString(mysqlUrl.getPort());
            runMysqlUtility(testDatabase, List.of(
                    "mysqldump", "--protocol=tcp", "--host=" + host, "--port=" + port,
                    "--user=" + testDatabase.username(), "--single-transaction", "--skip-lock-tables",
                    "--no-tablespaces", "--column-statistics=0", "--set-gtid-purged=OFF", DATABASE),
                    ProcessBuilder.Redirect.to(dump.toFile()), null, Duration.ofMinutes(3));
            boolean dumpContainsMarker;
            try (var lines = Files.lines(dump)) {
                dumpContainsMarker = lines.anyMatch(line -> line.contains(idempotencyKey));
            }
            if (Files.size(dump) == 0 || !dumpContainsMarker) {
                throw new IllegalStateException("Guarded database dump is empty or missing its fixture marker");
            }
            runMysqlUtility(testDatabase, List.of(
                    "mysql", "--protocol=tcp", "--host=" + host, "--port=" + port,
                    "--user=" + testDatabase.username(), "--database=" + DATABASE),
                    ProcessBuilder.Redirect.DISCARD, dump, Duration.ofMinutes(3));
        } finally {
            Files.deleteIfExists(dump);
        }

        assertDoesNotThrow(flyway::validate);
        assertEquals("57", flyway.info().current().getVersion().getVersion());
        assertEquals(before, dumpRestoreCounts(jdbc));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from idempotency_records
                where actor_id=? and idempotency_key=? and operation_id=?
                """, Integer.class, userId, idempotencyKey, operationId));
    }

    private static Map<String, Integer> dumpRestoreCounts(JdbcTemplate jdbc) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : List.of("flyway_schema_history", "users", "idempotency_records",
                "payout_verification_records", "wallet_withdrawal_requests",
                "deposit_accounting_journals", "deposit_accounting_journal_lines",
                "withdrawal_transfer_reference_reservations_v57",
                "withdrawal_transfer_reference_reservation_sources_v57")) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Integer.class));
        }
        return counts;
    }

    private static void runMysqlUtility(TestDatabase database, List<String> command,
            ProcessBuilder.Redirect output, Path input, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(output);
        if (input != null) {
            builder.redirectInput(input.toFile());
        }
        // Keep the password out of process arguments and all test logs.
        builder.environment().put("MYSQL_PWD", database.password());
        Process process = builder.start();
        if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("MySQL dump/restore command exceeded its time limit");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Guarded MySQL dump/restore command failed with exit code "
                    + process.exitValue());
        }
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
