package com.bakaroo.bingo.backend.common.idempotency;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the V55 schema after Flyway has upgraded the guarded MySQL schema. */
@SpringBootTest
@ActiveProfiles("test")
class IdempotencyActorSchemaIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayUpgradeRequiresAnExistingUserForEveryIdempotencyActor() {
        assertEquals(1, jdbcTemplate.queryForObject("""
                select is_nullable = 'NO'
                from information_schema.columns
                where table_schema = database()
                  and table_name = 'idempotency_records'
                  and column_name = 'actor_id'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.key_column_usage
                where table_schema = database()
                  and table_name = 'idempotency_records'
                  and constraint_name = 'fk_idempotency_records_actor'
                  and column_name = 'actor_id'
                  and referenced_table_name = 'users'
                  and referenced_column_name = 'id'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = 'idempotency_records'
                  and index_name = 'uk_idempotency_records_actor_key'
                  and non_unique = 0
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                from flyway_schema_history
                where version = '55' and success = 1
                """, Integer.class));
    }
}
