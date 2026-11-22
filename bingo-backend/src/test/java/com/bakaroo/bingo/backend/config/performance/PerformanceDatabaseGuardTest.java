package com.bakaroo.bingo.backend.config.performance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PerformanceDatabaseGuardTest {

    private static final String SCHEMA = "bingo_perf_run_2026";
    private static final String PLAYER = "bingo_perf_runner@localhost";
    private static final String SERVER_UUID = "01234567-89ab-cdef-0123-456789abcdef";
    private static final List<String> SCOPED_GRANTS = List.of(
            "GRANT USAGE ON *.* TO `bingo_perf_runner`@`localhost`",
            "GRANT SELECT, INSERT, UPDATE, DELETE ON `bingo_perf_run_2026`.* TO `bingo_perf_runner`@`localhost`");

    @Test
    void acceptsOnlyTheExpectedDisposableSchemaAccountPortAndSchemaScopedGrants() {
        assertThatCode(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA, SCHEMA, 3306, 3306, SERVER_UUID, SERVER_UUID, PLAYER, 10, 20, SCOPED_GRANTS))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAProductionOrPersistentSchemaName() {
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                "bingo", "bingo", 3306, 3306, SERVER_UUID, SERVER_UUID, PLAYER, 10, 20, SCOPED_GRANTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dedicated bingo_perf_");
    }

    @Test
    void rejectsRuntimeSchemaMismatch() {
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA, "bingo_perf_other_run", 3306, 3306, SERVER_UUID, SERVER_UUID, PLAYER, 10, 20, SCOPED_GRANTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsUnexpectedDatabaseAccountOrPort() {
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA, SCHEMA, 3306, 3306, SERVER_UUID, SERVER_UUID, "bingo_app@%", 10, 20, SCOPED_GRANTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bingo_perf_runner");
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA, SCHEMA, 3306, 3307, SERVER_UUID, SERVER_UUID, PLAYER, 10, 20, SCOPED_GRANTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA, SCHEMA, 3306, 3306, SERVER_UUID, "fedcba98-7654-3210-fedc-ba9876543210", PLAYER,
                10, 20, SCOPED_GRANTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server UUID");
    }

    @Test
    void rejectsCrossSchemaAndGrantOptionPrivileges() {
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA,
                SCHEMA,
                3306,
                3306,
                SERVER_UUID,
                SERVER_UUID,
                PLAYER,
                10,
                20,
                List.of("GRANT SELECT ON `bingo`.* TO `bingo_perf_runner`@`localhost`")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside its isolated catalog");
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA,
                SCHEMA,
                3306,
                3306,
                SERVER_UUID,
                SERVER_UUID,
                PLAYER,
                10,
                20,
                List.of("GRANT ALL PRIVILEGES ON `bingo_perf_run_2026`.* TO `bingo_perf_runner`@`localhost` WITH GRANT OPTION")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside its isolated catalog");
    }

    @Test
    void rejectsMissingExplicitSchemaGrant() {
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA,
                SCHEMA,
                3306,
                3306,
                SERVER_UUID,
                SERVER_UUID,
                PLAYER,
                10,
                20,
                List.of("GRANT USAGE ON *.* TO `bingo_perf_runner`@`localhost`")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no explicit grant");
    }

    @Test
    void rejectsConnectionPoolsAboveTheApprovedDatabaseCeiling() {
        assertThatThrownBy(() -> PerformanceDatabaseGuard.validateRuntimeIdentity(
                SCHEMA, SCHEMA, 3306, 3306, SERVER_UUID, SERVER_UUID, PLAYER, 21, 20, SCOPED_GRANTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved database connection ceiling");
    }
}
