package com.equar.bingo.backend.feature.support;

import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.support.dto.SupportTicketCreateRequest;
import com.equar.bingo.backend.feature.support.dto.SupportTicketMessageRequest;
import com.equar.bingo.backend.feature.support.dto.SupportTicketStatusUpdateRequest;
import com.equar.bingo.backend.feature.support.entity.SupportTicketCategory;
import com.equar.bingo.backend.feature.support.entity.SupportTicketPriority;
import com.equar.bingo.backend.feature.support.entity.SupportTicketStatus;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportIntegrationTest {

        @Autowired
        MockMvc mockMvc;

        @Autowired
        ObjectMapper objectMapper;

        @Autowired
        UserRepository userRepository;

        @Autowired
        PasswordEncoder passwordEncoder;

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

        private User player;
        private User admin;
        private String playerPhoneNumber;
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
                adminPhoneNumber = String.format("09%08d", (uniqueSeed + 1) % 100_000_000L);

                Instant now = Instant.now();
                player = userRepository.save(User.create(
                                phoneNormalizer.normalize(playerPhoneNumber),
                                "Support Player",
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

        private String loginToken(String phoneNumber, String password) throws Exception {
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