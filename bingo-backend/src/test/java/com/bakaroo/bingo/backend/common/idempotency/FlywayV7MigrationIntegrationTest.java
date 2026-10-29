package com.bakaroo.bingo.backend.common.idempotency;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class FlywayV7MigrationIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void v7CreatesActorScopedIdempotencyAndPayoutConstraintsOnMysql() {
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.table_constraints
                where table_schema = database()
                  and table_name = 'idempotency_records'
                  and constraint_type = 'PRIMARY KEY'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.key_column_usage
                where table_schema = database()
                  and table_name = 'idempotency_records'
                  and constraint_name = 'PRIMARY'
                  and column_name = 'id'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(distinct index_name)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = 'idempotency_records'
                  and index_name = 'uk_idempotency_records_actor_key'
                  and non_unique = 0
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.statistics
                where table_schema = database()
                  and table_name = 'payout_verification_records'
                  and index_name = 'uk_payout_verification_records_operation_id'
                  and non_unique = 0
                """, Integer.class));
        String referencedTable = jdbcTemplate.queryForObject("""
                select referenced_table_name
                from information_schema.key_column_usage
                where table_schema = database()
                  and table_name = 'payout_verification_records'
                  and constraint_name = 'fk_payout_verification_entitlement'
                """, String.class);
        assertNotNull(referencedTable);
        assertEquals("bingo_prize_entitlements", referencedTable);
    }
}
