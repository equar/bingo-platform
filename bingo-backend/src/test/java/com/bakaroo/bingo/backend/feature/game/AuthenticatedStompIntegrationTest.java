package com.bakaroo.bingo.backend.feature.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bakaroo.bingo.backend.MySqlIntegrationTest;
import com.bakaroo.bingo.backend.feature.auth.dto.AuthResponse;
import com.bakaroo.bingo.backend.feature.auth.dto.LoginRequest;
import com.bakaroo.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.bakaroo.bingo.backend.feature.game.dto.BingoPrizeConfigRequest;
import com.bakaroo.bingo.backend.feature.game.dto.BingoRoomRequest;
import com.bakaroo.bingo.backend.feature.game.dto.DrawEventResponse;
import com.bakaroo.bingo.backend.feature.game.dto.GameSessionRequest;
import com.bakaroo.bingo.backend.feature.game.dto.GameSessionStateUpdateRequest;
import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.entity.PrizeSettlementPath;
import com.bakaroo.bingo.backend.feature.game.service.BingoGameService;
import com.bakaroo.bingo.backend.feature.game.websocket.GameLiveDrawService;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthenticatedStompIntegrationTest extends MySqlIntegrationTest {

    private static final String PLAYER_PIN = "123456";

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EthiopianPhoneNormalizer phoneNormalizer;

    @Autowired
    BingoGameService bingoGameService;

    @Autowired
    GameLiveDrawService gameLiveDrawService;

    @Autowired
    ObjectMapper objectMapper;

    private String playerPhone;
    private Long sessionId;
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        playerPhone = randomPhone();
        User player = userRepository.save(User.create(
                phoneNormalizer.normalize(playerPhone),
                "WebSocket QA Player " + suffix,
                passwordEncoder.encode(PLAYER_PIN),
                AccountStatus.ACTIVE,
                java.util.Set.of(UserRole.PLAYER),
                Instant.now()));

        var room = bingoGameService.createRoom(new BingoRoomRequest(
                "STOMP QA Room " + suffix,
                "Disposable authenticated WebSocket fixture",
                new BigDecimal("4.00"),
                "ETB",
                "Africa/Addis_Ababa",
                "0 0/30 * * * *",
                10,
                10,
                true,
                true,
                true,
                List.of(BingoPatternType.LINE),
                List.of(new BingoPrizeConfigRequest(
                        BingoPatternType.LINE,
                        new BigDecimal("10.00"),
                        PrizeSettlementPath.PLAYER_WALLET,
                        1)),
                null));
        var session = bingoGameService.createSession(
                room.id(),
                new GameSessionRequest(
                        "STOMP QA Session " + suffix,
                        Instant.now().plusSeconds(60),
                        GameSessionState.DRAFT,
                        new BigDecimal("4.00"),
                        "ETB",
                        10,
                        10,
                        true,
                        true,
                        List.of(BingoPatternType.LINE),
                        null));
        bingoGameService.updateSessionState(
                session.id(),
                new GameSessionStateUpdateRequest(GameSessionState.OPEN));
        bingoGameService.updateSessionState(
                session.id(),
                new GameSessionStateUpdateRequest(GameSessionState.STARTED));
        sessionId = session.id();
    }

    @AfterEach
    void stopStompClient() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void authenticatedClientReceivesOrderedDrawsAndReplaysMissedSequenceAfterReconnect() throws Exception {
        String token = loginToken();
        List<DrawEventResponse> received = new CopyOnWriteArrayList<>();
        CountDownLatch firstDraw = new CountDownLatch(1);
        WebSocketStompClient client = createClient();
        StompSession session = connect(client, token);
        String destination = "/topic/session/" + sessionId + "/draws";
        session.subscribe(destination, drawHandler(received, firstDraw));
        Thread.sleep(250);

        Instant firstDue = Instant.now().plusSeconds(30);
        gameLiveDrawService.advanceSessionDrawIfDue(sessionId, firstDue).orElseThrow();
        assertTrue(firstDraw.await(10, TimeUnit.SECONDS));
        assertEquals(1, received.get(0).sequenceNo());

        session.disconnect();
        gameLiveDrawService.advanceSessionDrawIfDue(sessionId, firstDue.plusSeconds(30)).orElseThrow();
        List<DrawEventResponse> replay = fetchDraws(token, 1);
        assertEquals(1, replay.size());
        assertEquals(2, replay.get(0).sequenceNo());

        CountDownLatch nextDraw = new CountDownLatch(1);
        StompSession reconnected = connect(client, token);
        reconnected.subscribe(destination, drawHandler(received, nextDraw));
        Thread.sleep(250);
        gameLiveDrawService.advanceSessionDrawIfDue(sessionId, firstDue.plusSeconds(60)).orElseThrow();
        assertTrue(nextDraw.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(1, 3), received.stream().map(DrawEventResponse::sequenceNo).toList());

        reconnected.disconnect();
        assertThrows(Exception.class, () -> connect(client, "not-a-valid-token"));
    }

    private WebSocketStompClient createClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter(objectMapper));
        return stompClient;
    }

    private StompSession connect(WebSocketStompClient client, String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return client.connectAsync(
                URI.create("ws://localhost:" + port + "/ws"),
                new WebSocketHttpHeaders(),
                headers,
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
    }

    private StompFrameHandler drawHandler(List<DrawEventResponse> received, CountDownLatch latch) {
        return new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                return DrawEventResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((DrawEventResponse) payload);
                latch.countDown();
            }
        };
    }

    private String loginToken() throws Exception {
        RestTemplate http = new RestTemplate();
        ResponseEntity<AuthResponse> response = http.postForEntity(
                "http://localhost:" + port + "/api/v1/auth/login",
                new HttpEntity<>(
                        new LoginRequest(playerPhone, PLAYER_PIN),
                        jsonHeaders()),
                AuthResponse.class);
        assertEquals(200, response.getStatusCode().value());
        return response.getBody().accessToken();
    }

    private List<DrawEventResponse> fetchDraws(String token, long afterSequence) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = new RestTemplate().exchange(
                "http://localhost:" + port + "/api/v1/sessions/" + sessionId
                        + "/draws?afterSequence=" + afterSequence,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertEquals(200, response.getStatusCode().value());
        return objectMapper.readValue(response.getBody(), new TypeReference<>() {
        });
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String randomPhone() {
        return String.format(Locale.ROOT, "09%08d", new java.security.SecureRandom().nextInt(100_000_000));
    }
}
