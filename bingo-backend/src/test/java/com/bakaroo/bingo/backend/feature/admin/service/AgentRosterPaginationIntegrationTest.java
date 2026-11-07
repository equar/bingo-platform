package com.bakaroo.bingo.backend.feature.admin.service;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bakaroo.bingo.backend.AgentPaginationMySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.agent.entity.Agent;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentFloat;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentFloatTransaction;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentFloatTransactionType;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import com.bakaroo.bingo.backend.feature.agent.entity.FloatTopUpRequest;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.AgentRepository;
import com.bakaroo.bingo.backend.feature.agent.repository.FloatTopUpRequestRepository;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
        + "com.bakaroo.bingo.backend.feature.admin.service.AgentRosterSqlInspector")
@Transactional
@Rollback
class AgentRosterPaginationIntegrationTest extends AgentPaginationMySqlIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AgentRepository agentRepository;
    @Autowired AgentFloatRepository agentFloatRepository;
    @Autowired AgentFloatTransactionRepository agentFloatTransactionRepository;
    @Autowired FloatTopUpRequestRepository floatTopUpRequestRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    private final List<Long> northwindPendingAgentIds = new ArrayList<>();

    @BeforeEach
    void insertSyntheticRollbackFixtures() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        Agent first = createAgent("Pagination Northwind One", "+251900100001", AgentStatus.PENDING,
                "Northwind", "Zone A", now);
        createAgent("Pagination Northwind Two", "+251900100002", AgentStatus.PENDING,
                "Northwind", "Zone B", now);
        createAgent("Pagination Northwind Approved", "+251900100003", AgentStatus.APPROVED,
                "Northwind", "Zone C", now);
        createAgent("Pagination Eastfield Pending", "+251900100004", AgentStatus.PENDING,
                "Eastfield", "Zone D", now);
        jdbcTemplate.update("UPDATE bingo_agents SET created_at = ? WHERE id IN (?, ?, ?, ?)",
                now, northwindPendingAgentIds.get(0), northwindPendingAgentIds.get(1),
                jdbcTemplate.queryForObject("SELECT id FROM bingo_agents WHERE user_id = ?",
                        Long.class, userIdByPhone("+251900100003")),
                jdbcTemplate.queryForObject("SELECT id FROM bingo_agents WHERE user_id = ?",
                        Long.class, userIdByPhone("+251900100004")));

        AgentFloat agentFloat = agentFloatRepository.save(AgentFloat.create(first, "ETB",
                new BigDecimal("100.00"), BigDecimal.ZERO));
        agentFloatTransactionRepository.save(AgentFloatTransaction.create(agentFloat,
                AgentFloatTransactionType.OPENING_BALANCE, new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, "TEST_FIXTURE", null,
                UUID.randomUUID().toString(), "Synthetic pagination fixture"));
        floatTopUpRequestRepository.save(FloatTopUpRequest.create(first, first.getUser(),
                new BigDecimal("1.00"), "ETB", UUID.randomUUID().toString()));
    }

    @Test
    void filtersInDatabasePagesWithStableOrderingAndAccurateTotals() throws Exception {
        MvcResult firstPage = mockMvc.perform(get("/api/v1/admin/agents")
                        .param("query", "  NORTHWIND ").param("status", "PENDING")
                        .param("page", "0").param("size", "1")
                        .with(user("pagination-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andReturn();

        long firstId = objectMapperReadLong(firstPage, "$.content[0].agentId");
        MvcResult secondPage = mockMvc.perform(get("/api/v1/admin/agents")
                        .param("query", "northwind").param("status", "PENDING")
                        .param("page", "1").param("size", "1")
                        .with(user("pagination-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true))
                .andReturn();
        long secondId = objectMapperReadLong(secondPage, "$.content[0].agentId");
        assertEquals(northwindPendingAgentIds.get(1), firstId,
                "equal created_at values must sort by descending agent id");
        assertEquals(northwindPendingAgentIds.get(0), secondId);
    }

    @Test
    void returnsSyntheticBatchedRelatedCountsUsingGroupedDatabaseQueries() throws Exception {
        AgentRosterSqlInspector.beginCapture();
        MvcResult result;
        List<String> statements;
        try {
            result = mockMvc.perform(get("/api/v1/admin/agents")
                            .param("query", "pagination northwind").param("page", "0").param("size", "3")
                            .with(user("pagination-admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(3)))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andReturn();
        } finally {
            statements = AgentRosterSqlInspector.endCapture();
        }

        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).path("content");
        JsonNode firstAgent = null;
        for (JsonNode summary : content) {
            if (summary.path("agentId").asLong() == northwindPendingAgentIds.get(0)) {
                firstAgent = summary;
                break;
            }
        }
        assertTrue(firstAgent != null, "synthetic Agent should be present on the requested page");
        assertEquals(1, firstAgent.path("floatTransactionCount").asLong());
        assertEquals(1, firstAgent.path("topUpRequestCount").asLong());

        List<String> grouped = statements.stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT))
                .filter(sql -> sql.contains(" group by "))
                .toList();
        assertEquals(4, grouped.size(), "the page should issue one grouped query for each related count type");
        assertTrue(grouped.stream().anyMatch(sql -> sql.contains("bingo_tickets")));
        assertTrue(grouped.stream().anyMatch(sql -> sql.contains("agent_float_transactions")));
        assertTrue(grouped.stream().anyMatch(sql -> sql.contains("agent_float_top_up_requests")));
        assertTrue(grouped.stream().anyMatch(sql -> sql.contains("payout_verification_records")));
    }

    @Test
    void requiresAdminRoleAndRejectsInvalidPaginationOverHttp() throws Exception {
        mockMvc.perform(get("/api/v1/admin/agents"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/agents").with(user("pagination-support").roles("SUPPORT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/agents").param("page", "0").param("size", "101")
                        .with(user("pagination-admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private Agent createAgent(String name, String phone, AgentStatus status, String city,
            String territory, Instant createdAt) {
        User user = userRepository.save(User.create(phone, name, "synthetic-not-a-login-hash",
                AccountStatus.ACTIVE, Set.of(UserRole.AGENT), createdAt));
        Agent agent = agentRepository.save(Agent.create(user, status, city, territory,
                "SYNTHETIC", UUID.randomUUID().toString(), null, null));
        if ("Northwind".equals(city) && status == AgentStatus.PENDING) {
            northwindPendingAgentIds.add(agent.getId());
        }
        return agent;
    }

    private Long userIdByPhone(String phone) {
        return userRepository.findByNormalizedPhone(phone).orElseThrow().getId();
    }

    private long objectMapperReadLong(MvcResult result, String expression) throws Exception {
        return new org.springframework.test.util.JsonPathExpectationsHelper(expression)
                .evaluateJsonPath(result.getResponse().getContentAsString(), Long.class);
    }
}
