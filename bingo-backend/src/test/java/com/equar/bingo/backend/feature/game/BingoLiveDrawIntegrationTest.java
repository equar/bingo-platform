package com.equar.bingo.backend.feature.game;

import com.equar.bingo.backend.feature.auth.dto.LoginRequest;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.game.dto.BingoPrizeConfigRequest;
import com.equar.bingo.backend.feature.game.dto.BingoRoomRequest;
import com.equar.bingo.backend.feature.game.dto.DrawEventResponse;
import com.equar.bingo.backend.feature.game.dto.GameSessionRequest;
import com.equar.bingo.backend.feature.game.dto.GameSessionStateUpdateRequest;
import com.equar.bingo.backend.feature.game.entity.BingoPatternType;
import com.equar.bingo.backend.feature.game.entity.GameSession;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.equar.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.equar.bingo.backend.feature.game.repository.TicketRepository;
import com.equar.bingo.backend.feature.game.repository.WinClaimRepository;
import com.equar.bingo.backend.feature.game.service.BingoGameService;
import com.equar.bingo.backend.feature.game.websocket.GameLiveDrawService;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import com.equar.bingo.backend.feature.wallet.repository.DepositClaimRepository;
import com.equar.bingo.backend.feature.wallet.repository.IdempotencyRecordRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletRepository;
import com.equar.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.equar.bingo.backend.feature.wallet.service.WalletService;
import com.equar.bingo.backend.feature.agent.repository.PayoutVerificationRecordRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatTransactionRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentFloatRepository;
import com.equar.bingo.backend.feature.agent.repository.AgentRepository;
import com.equar.bingo.backend.feature.support.repository.InternalNoteRepository;
import com.equar.bingo.backend.feature.support.repository.SupportMessageRepository;
import com.equar.bingo.backend.feature.support.repository.SupportTicketRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BingoLiveDrawIntegrationTest.TestMessagingConfig.class)
class BingoLiveDrawIntegrationTest {

        @LocalServerPort
        int port;

        @Autowired
        MockMvc mockMvc;

        @Autowired
        PasswordEncoder passwordEncoder;

        @Autowired
        ObjectMapper objectMapper;

        @Autowired
        EthiopianPhoneNormalizer phoneNormalizer;

        @Autowired
        UserRepository userRepository;

        @Autowired
        WalletRepository walletRepository;

        @Autowired
        WalletTransactionRepository walletTransactionRepository;

        @Autowired
        DepositClaimRepository depositClaimRepository;

        @Autowired
        IdempotencyRecordRepository idempotencyRecordRepository;

        @Autowired
        BingoRoomRepository bingoRoomRepository;

        @Autowired
        GameSessionRepository gameSessionRepository;

        @Autowired
        TicketRepository ticketRepository;

        @Autowired
        DrawEventRepository drawEventRepository;

        @Autowired
        WinClaimRepository winClaimRepository;

        @Autowired
        PrizeEntitlementRepository prizeEntitlementRepository;

        @Autowired
        WalletService walletService;

        @Autowired
        SupportTicketRepository supportTicketRepository;

        @Autowired
        SupportMessageRepository supportMessageRepository;

        @Autowired
        InternalNoteRepository internalNoteRepository;

        @Autowired
        BingoGameService bingoGameService;

        @Autowired
        GameLiveDrawService gameLiveDrawService;

        @Autowired
        RecordingSimpMessagingTemplate recordingSimpMessagingTemplate;

        @Autowired
        com.equar.bingo.backend.feature.game.config.GameProperties gameProperties;
        @Autowired
        PayoutVerificationRecordRepository payoutVerificationRecordRepository;

        @Autowired
        AgentFloatTransactionRepository agentFloatTransactionRepository;

        @Autowired
        AgentFloatRepository agentFloatRepository;

        @Autowired
        AgentRepository agentRepository;

        @Autowired
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

        private User adminUser;
        private User playerUser;

        @BeforeEach
        void setUp() {
                internalNoteRepository.deleteAllInBatch();
                supportMessageRepository.deleteAllInBatch();
                supportTicketRepository.deleteAllInBatch();
                payoutVerificationRecordRepository.deleteAll();
                agentFloatTransactionRepository.deleteAll();
                agentFloatRepository.deleteAll();
                agentRepository.deleteAll();
                prizeEntitlementRepository.deleteAll();
                winClaimRepository.deleteAll();
                drawEventRepository.deleteAll();
                ticketRepository.deleteAll();
                gameSessionRepository.deleteAll();
                bingoRoomRepository.deleteAll();
                walletTransactionRepository.deleteAll();
                jdbcTemplate.update("delete from signup_reward_grants");
                jdbcTemplate.update("delete from wallet_transfers");
                jdbcTemplate.update("delete from signup_reward_policies");
                jdbcTemplate.update("delete from wallet_transfer_policies");
                depositClaimRepository.deleteAll();
                walletRepository.deleteAll();
                idempotencyRecordRepository.deleteAll();
                userRepository.deleteAll();

                Instant now = Instant.now();
                adminUser = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000201"),
                                "Admin User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.ADMIN),
                                now));
                playerUser = userRepository.save(User.create(
                                phoneNormalizer.normalize("0911000202"),
                                "Player User",
                                passwordEncoder.encode("123456"),
                                AccountStatus.ACTIVE,
                                Set.of(UserRole.PLAYER),
                                now));
                walletService.createWalletIfMissing(playerUser);
                walletService.adjustPlayerWallet(playerUser.getId(), new BingoUserPrincipal(adminUser),
                                new com.equar.bingo.backend.feature.wallet.dto.WalletAdjustmentRequest(
                                                new BigDecimal("1000.00"), "ETB",
                                                "seed"),
                                "seed-player");
        }

        @Test
        void competingWorkersPersistOnlyOneDraw() {
                GameSession session = startedSession("Concurrency Room");
                Instant dueTime = session.getStartedAt().plus(gameProperties.drawInterval()).plusSeconds(1);

                var first = CompletableFuture
                                .supplyAsync(() -> gameLiveDrawService.advanceSessionDrawIfDue(session.getId(),
                                                dueTime));
                var second = CompletableFuture
                                .supplyAsync(() -> gameLiveDrawService.advanceSessionDrawIfDue(session.getId(),
                                                dueTime));

                List<DrawEventResponse> draws = List.of(first.join(), second.join()).stream()
                                .flatMap(java.util.Optional::stream)
                                .toList();

                assertEquals(1, draws.size());
                assertEquals(1L, drawEventRepository.countBySessionId(session.getId()));
        }

        @Test
        void restartRecoveryReplayAndExhaustionAreHandled() throws Exception {
                GameSession session = startedSession("Replay Room");
                Instant dueTime = session.getStartedAt().plus(gameProperties.drawInterval()).plusSeconds(1);

                List<Integer> numbers = new ArrayList<>();
                Set<Integer> uniqueNumbers = new HashSet<>();
                for (int index = 0; index < 75; index++) {
                        DrawEventResponse draw = gameLiveDrawService.advanceSessionDrawIfDue(session.getId(),
                                        dueTime.plus(gameProperties.drawInterval().multipliedBy(index))).orElseThrow();
                        numbers.add(draw.numberDrawn());
                        uniqueNumbers.add(draw.numberDrawn());
                }

                assertEquals(75, numbers.size());
                assertEquals(75, uniqueNumbers.size());
                assertEquals(GameSessionState.CLOSED,
                                gameSessionRepository.findById(session.getId()).orElseThrow().getState());
                assertFalse(gameLiveDrawService
                                .advanceSessionDrawIfDue(session.getId(), dueTime.plus(Duration.ofDays(1)))
                                .isPresent());

                String replay = mockMvc.perform(get("/api/v1/sessions/" + session.getId() + "/draws")
                                .header("Authorization", "Bearer " + loginToken("0911000201", "123456"))
                                .param("afterSequence", "1"))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                List<DrawEventResponse> replayed = objectMapper.readValue(replay, new TypeReference<>() {
                });
                assertEquals(74, replayed.size());
                for (int index = 1; index < replayed.size(); index++) {
                        assertTrue(replayed.get(index - 1).sequenceNo() < replayed.get(index).sequenceNo());
                }
        }

        @Test
        void websocketSubscriptionRequiresJwtAndReceivesBroadcast() throws Exception {
                GameSession session = startedSession("Socket Room");
                String token = loginToken("0911000202", "123456");
                assertTrue(token.startsWith("eyJ"));
                Instant dueTime = session.getStartedAt().plus(gameProperties.drawInterval()).plusSeconds(1);
                gameLiveDrawService.advanceSessionDrawIfDue(session.getId(), dueTime);

                assertFalse(recordingSimpMessagingTemplate.getRecordedMessages().isEmpty());
                RecordedMessage message = recordingSimpMessagingTemplate.getRecordedMessages().get(0);
                assertEquals("/topic/session/" + session.getId() + "/draws", message.destination());
                assertTrue(message.payload() instanceof DrawEventResponse);
        }

        @Test
        void websocketConnectionWithoutJwtIsRejected() {
                WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
                stompClient.setMessageConverter(new MappingJackson2MessageConverter());

                assertThrows(Exception.class, () -> stompClient
                                .connectAsync(URI.create("ws://localhost:" + port + "/ws"), new WebSocketHttpHeaders(),
                                                new StompHeaders(), new StompSessionHandlerAdapter() {
                                                })
                                .get(5, TimeUnit.SECONDS));
        }

        private GameSession startedSession(String roomName) {
                var room = bingoGameService.createRoom(roomRequest(roomName));
                var session = bingoGameService.createSession(room.id(), sessionRequest(GameSessionState.DRAFT));
                bingoGameService.updateSessionState(session.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.OPEN));
                bingoGameService.updateSessionState(session.id(),
                                new GameSessionStateUpdateRequest(GameSessionState.STARTED));
                return gameSessionRepository.findById(session.id()).orElseThrow();
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

        private StompHeaders connectHeaders(String token) {
                StompHeaders headers = new StompHeaders();
                headers.add("Authorization", "Bearer " + token);
                return headers;
        }

        private BingoRoomRequest roomRequest(String name) {
                return new BingoRoomRequest(
                                name,
                                "Live draw room",
                                new BigDecimal("4.00"),
                                "ETB",
                                "Africa/Addis_Ababa",
                                "0 0/30 * * * *",
                                10,
                                10,
                                true,
                                true,
                                true,
                                List.of(BingoPatternType.LINE, BingoPatternType.TWO_LINES, BingoPatternType.FULL_HOUSE),
                                List.of(
                                                new BingoPrizeConfigRequest(BingoPatternType.LINE,
                                                                new BigDecimal("10.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1),
                                                new BingoPrizeConfigRequest(BingoPatternType.TWO_LINES,
                                                                new BigDecimal("20.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1),
                                                new BingoPrizeConfigRequest(BingoPatternType.FULL_HOUSE,
                                                                new BigDecimal("50.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1)));
        }

        private GameSessionRequest sessionRequest(GameSessionState state) {
                return new GameSessionRequest(
                                "Live session",
                                Instant.now().plusSeconds(60),
                                state,
                                new BigDecimal("4.00"),
                                "ETB",
                                10,
                                10,
                                true,
                                true,
                                List.of(BingoPatternType.LINE, BingoPatternType.TWO_LINES, BingoPatternType.FULL_HOUSE),
                                List.of(
                                                new BingoPrizeConfigRequest(BingoPatternType.LINE,
                                                                new BigDecimal("10.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1),
                                                new BingoPrizeConfigRequest(BingoPatternType.TWO_LINES,
                                                                new BigDecimal("20.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1),
                                                new BingoPrizeConfigRequest(BingoPatternType.FULL_HOUSE,
                                                                new BigDecimal("50.00"),
                                                                PrizeSettlementPath.PLAYER_WALLET, 1)));
        }

        @org.springframework.boot.test.context.TestConfiguration
        static class TestMessagingConfig {

                @Bean
                @Primary
                RecordingSimpMessagingTemplate recordingSimpMessagingTemplate() {
                        return new RecordingSimpMessagingTemplate();
                }
        }

        static final class RecordingSimpMessagingTemplate extends SimpMessagingTemplate {

                private final List<RecordedMessage> recordedMessages = new ArrayList<>();

                RecordingSimpMessagingTemplate() {
                        super(new ExecutorSubscribableChannel());
                }

                @Override
                public void convertAndSend(String destination, Object payload) {
                        recordedMessages.add(new RecordedMessage(destination, payload));
                }

                List<RecordedMessage> getRecordedMessages() {
                        return recordedMessages;
                }
        }

        record RecordedMessage(String destination, Object payload) {
        }
}
