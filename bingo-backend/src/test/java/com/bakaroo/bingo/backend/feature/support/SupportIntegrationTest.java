package com.bakaroo.bingo.backend.feature.support;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.auth.dto.LoginRequest;
import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.auth.service.JwtService;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketCreateRequest;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketMessageRequest;
import com.bakaroo.bingo.backend.feature.support.dto.SupportTicketStatusUpdateRequest;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketPriority;
import com.bakaroo.bingo.backend.feature.support.entity.SupportTicketStatus;
import com.bakaroo.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.bakaroo.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.bakaroo.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportIntegrationTest extends MySqlIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        ObjectMapper objectMapper;

        @Autowired
        UserRepository userRepository;

        @Autowired
        PasswordEncoder passwordEncoder;

        @Autowired
        JwtService jwtService;

        @Autowired
        EthiopianPhoneNormalizer phoneNormalizer;

        @Autowired
        SupportTicketRepository supportTicketRepository;

        @Autowired
        SupportMessageRepository supportMessageRepository;

        @Autowired
        InternalNoteRepository internalNoteRepository;

        @Autowired
        WalletTransactionRepository walletTransactionRepository;

        @Autowired
        DepositClaimRepository depositClaimRepository;

        @Autowired
        WalletRepository walletRepository;

        @Autowired
        IdempotencyRecordRepository idempotencyRecordRepository;

        @Autowired
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        private User player;
        private User otherPlayer;
        private User admin;
        private User support;
        private String playerPhoneNumber;
        private String otherPlayerPhoneNumber;
        private String adminPhoneNumber;

        @BeforeEach
        void setUp() {
                internalNoteRepository.deleteAllInBatch();
                supportMessageRepository.deleteAllInBatch();
                supportTicketRepository.deleteAllInBatch();
                walletTransactionRepository.deleteAllInBatch();
                depositClaimRepository.deleteAllInBatch();
                walletRepository.deleteAllInBatch();
                idempotencyRecordRepository.deleteAllInBatch();

                long uniqueSeed = System.currentTimeMillis() % 100_000_000L;
                playerPhoneNumber = String.format("09%08d", uniqueSeed);
                otherPlayerPhoneNumber = String.format("09%08d", (uniqueSeed + 3) % 100_000_000L);
                adminPhoneNumber = String.format("09%08d", (uniqueSeed + 1) % 100_000_000L);

                Instant now = Instant.now();
                player = userRepository.save(User.create(
                                phoneNormalizer.normalize(playerPhoneNumber),
                                "Support Player",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                now));
                otherPlayer = userRepository.save(User.create(
                                phoneNormalizer.normalize(otherPlayerPhoneNumber),
                                "Other Support Player",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                now));
                admin = userRepository.save(User.create(
                                phoneNormalizer.normalize(adminPhoneNumber),
                                "Support Admin",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN),
                                now));
                support = userRepository.save(User.create(
                                phoneNormalizer.normalize(String.format("09%08d", (uniqueSeed + 2) % 100_000_000L)),
                                "Support Agent",
                                passwordEncoder.encode("SupportPass123!"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.SUPPORT),
                                now));
        }

        @Test
        void supportTicketFlowHidesInternalNotesFromRequesterAndRequiresResolutionSummary() throws Exception {
                String playerToken = loginToken(playerPhoneNumber, "123456");
                String adminToken = loginToken(adminPhoneNumber, "123456");

                String createResponse = mockMvc.perform(post("/api/v1/support/tickets")
                                .header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new SupportTicketCreateRequest(
                                                "Wallet issue",
                                                "Top-up did not arrive",
                                                SupportTicketCategory.WALLET,
                                                SupportTicketPriority.HIGH,
                                                null,
                                                null,
                                                null))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.messages[0].body").value("Top-up did not arrive"))
                                .andExpect(jsonPath("$.internalNotes").isEmpty())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                JsonNode created = objectMapper.readTree(createResponse);
                long ticketId = created.get("id").asLong();

                mockMvc.perform(post("/api/v1/admin/support/tickets/" + ticketId + "/notes")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new SupportTicketMessageRequest("Internal follow-up"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.internalNotes[0].body").value("Internal follow-up"));

                mockMvc.perform(get("/api/v1/support/tickets/" + ticketId)
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.internalNotes").isEmpty())
                                .andExpect(jsonPath("$.messages[0].body").value("Top-up did not arrive"));

                mockMvc.perform(put("/api/v1/admin/support/tickets/" + ticketId + "/status")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new SupportTicketStatusUpdateRequest(
                                                SupportTicketStatus.CLOSED,
                                                null))))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("RESOLUTION_SUMMARY_REQUIRED"));

                mockMvc.perform(put("/api/v1/admin/support/tickets/" + ticketId + "/status")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new SupportTicketStatusUpdateRequest(
                                                SupportTicketStatus.RESOLVED,
                                                "Refunded manually"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("RESOLVED"))
                                .andExpect(jsonPath("$.resolutionSummary").value("Refunded manually"));
        }

        @Test
        void supportStaffCanMutateDedicatedTicketAndCannotAssignIneligibleAccounts() throws Exception {
                String playerToken = loginToken(playerPhoneNumber, "123456");
                String supportToken = jwtService.generateToken(support);
                String createResponse = mockMvc.perform(post("/api/v1/support/tickets")
                                .header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new SupportTicketCreateRequest(
                                                "QA support assignment", "Dedicated Module 02G test ticket",
                                                SupportTicketCategory.ACCOUNT, SupportTicketPriority.NORMAL,
                                                null, null, null))))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                long ticketId = objectMapper.readTree(createResponse).get("id").asLong();

                mockMvc.perform(post("/api/v1/admin/support/tickets/{id}/assign", ticketId)
                                .header("Authorization", "Bearer " + playerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.bakaroo.bingo.backend.feature.support.dto.SupportTicketAssignmentRequest(support.getId()))))
                                .andExpect(status().isForbidden());
                mockMvc.perform(post("/api/v1/admin/support/tickets/{id}/assign", ticketId)
                                .header("Authorization", "Bearer " + supportToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.bakaroo.bingo.backend.feature.support.dto.SupportTicketAssignmentRequest(player.getId()))))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(post("/api/v1/admin/support/tickets/{id}/assign", ticketId)
                                .header("Authorization", "Bearer " + supportToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new com.bakaroo.bingo.backend.feature.support.dto.SupportTicketAssignmentRequest(support.getId()))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.assignedToUserId").value(support.getId()))
                                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
                mockMvc.perform(post("/api/v1/admin/support/tickets/{id}/messages", ticketId)
                                .header("Authorization", "Bearer " + supportToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new SupportTicketMessageRequest("Support staff public reply"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.messages[1].body").value("Support staff public reply"));
                mockMvc.perform(get("/api/v1/support/tickets/{id}", ticketId)
                                .header("Authorization", "Bearer " + playerToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.messages[1].body").value("Support staff public reply"))
                                .andExpect(jsonPath("$.internalNotes").isEmpty());
                mockMvc.perform(put("/api/v1/admin/support/tickets/{id}/status", ticketId)
                                .header("Authorization", "Bearer " + supportToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new SupportTicketStatusUpdateRequest(
                                                SupportTicketStatus.CLOSED, "Module 02G support verification"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CLOSED"));
                mockMvc.perform(put("/api/v1/admin/support/tickets/{id}/status", ticketId)
                                .header("Authorization", "Bearer " + supportToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new SupportTicketStatusUpdateRequest(
                                                SupportTicketStatus.OPEN, null))))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("CLOSED_TICKET_TERMINAL"));
                mockMvc.perform(get("/api/v1/admin/support/tickets/{id}", ticketId)
                                .header("Authorization", "Bearer " + supportToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("CLOSED"))
                                .andExpect(jsonPath("$.assignedToUserId").value(support.getId()));
                String otherPlayerToken = loginToken(otherPlayerPhoneNumber, "123456");
                mockMvc.perform(get("/api/v1/support/tickets/{id}", ticketId)
                                .header("Authorization", "Bearer " + otherPlayerToken))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("SUPPORT_TICKET_OWNERSHIP_REQUIRED"));
                assertEquals(1, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where action='ASSIGN_TICKET' and actor_user_id=? and target_id=? and json_unquote(json_extract(metadata_json,'$.outcome'))='SUCCESS'",
                                Integer.class, support.getId(), ticketId));
                assertEquals(1, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where action='UPDATE_STATUS' and actor_user_id=? and target_id=? and json_unquote(json_extract(metadata_json,'$.outcome'))='SUCCESS'",
                                Integer.class, support.getId(), ticketId));
                assertEquals(1, jdbcTemplate.queryForObject(
                                "select count(*) from audit_log_entries where action='ADD_ADMIN_REPLY' and actor_user_id=? and target_id=? and json_unquote(json_extract(metadata_json,'$.outcome'))='SUCCESS'",
                                Integer.class, support.getId(), ticketId));
        }

        private String loginToken(String phoneNumber, String password) throws Exception {
                User user = userRepository.findByNormalizedPhone(phoneNormalizer.normalize(phoneNumber)).orElseThrow();
                if (!user.hasRole(UserRole.PLAYER)) {
                        return jwtService.generateToken(user);
                }
                String response = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new LoginRequest(phoneNumber, password))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                return objectMapper.readTree(response).get("accessToken").asText();
        }
}
