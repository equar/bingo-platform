package com.bakaroo.bingo.backend.feature.game.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.common.exception.ConflictException;
import com.bakaroo.bingo.backend.common.idempotency.IdempotentResult;
import com.bakaroo.bingo.backend.common.idempotency.IdempotencyService;
import com.bakaroo.bingo.backend.common.request.RequestHashService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.game.controller.PublicGameController;
import com.bakaroo.bingo.backend.feature.game.dto.WinClaimRequest;
import com.bakaroo.bingo.backend.feature.game.dto.WinClaimResponse;
import com.bakaroo.bingo.backend.feature.game.entity.BingoPatternType;
import com.bakaroo.bingo.backend.feature.game.entity.GameSession;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.entity.Ticket;
import com.bakaroo.bingo.backend.feature.game.config.GameProperties;
import com.bakaroo.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.bakaroo.bingo.backend.feature.game.repository.DrawEventRepository;
import com.bakaroo.bingo.backend.feature.game.repository.GameSessionRepository;
import com.bakaroo.bingo.backend.feature.game.repository.PrizeEntitlementRepository;
import com.bakaroo.bingo.backend.feature.game.repository.TicketRepository;
import com.bakaroo.bingo.backend.feature.game.repository.WinClaimRepository;
import com.bakaroo.bingo.backend.feature.game.websocket.GameLiveDrawService;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import com.bakaroo.bingo.backend.feature.wallet.service.LocalFinancialSimulationGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Arrays;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WinClaimSessionSecurityTest {

    private static final Long PLAYER_ID = 51L;
    private static final Long TICKET_ID = 81L;
    private static final Long SESSION_ID = 91L;

    @Mock private BingoRoomRepository bingoRoomRepository;
    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private DrawEventRepository drawEventRepository;
    @Mock private WinClaimRepository winClaimRepository;
    @Mock private PrizeEntitlementRepository prizeEntitlementRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private GameProperties gameProperties;
    @Mock private GameSnapshotMapper snapshotMapper;
    @Mock private IdempotencyService idempotencyService;
    @Mock private RequestHashService requestHashService;
    @Mock private ObjectMapper objectMapper;
    @Mock private LocalFinancialSimulationGuard ticketPurchasePostingGuard;
    @Mock private GameLiveDrawService gameLiveDrawService;

    private BingoGameService service;
    private User player;
    private Ticket ticket;
    private GameSession lockedSession;

    @BeforeEach
    void setUp() {
        service = new BingoGameService(bingoRoomRepository, gameSessionRepository, ticketRepository,
                drawEventRepository, winClaimRepository, prizeEntitlementRepository, userRepository,
                walletRepository, walletTransactionRepository, gameProperties, snapshotMapper,
                idempotencyService, requestHashService, objectMapper, ticketPurchasePostingGuard,
                gameLiveDrawService);

    }

    @Test
    void cancelledAuthoritativeSessionRejectsBeforeClaimCreationOrAwardSideEffects() {
        arrangeClaim(GameSessionState.CANCELLED);

        ConflictException failure = assertThrows(ConflictException.class,
                () -> submitClaim(new BingoUserPrincipal(player)));

        assertEquals("SESSION_CANCELLED", failure.getCode());
        verify(gameSessionRepository).findByIdForUpdate(SESSION_ID);
        verify(winClaimRepository, never()).save(any());
        verifyNoInteractions(drawEventRepository, prizeEntitlementRepository, walletRepository,
                walletTransactionRepository);
    }

    @Test
    void activeSessionPassesSessionGuardAndContinuesToExistingDuplicateValidation() {
        arrangeClaim(GameSessionState.STARTED);
        when(winClaimRepository.existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot(
                TICKET_ID, BingoPatternType.LINE, 0)).thenThrow(new ValidationReached());

        assertThrows(ValidationReached.class, () -> submitClaim(new BingoUserPrincipal(player)));

        verify(winClaimRepository).existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot(
                TICKET_ID, BingoPatternType.LINE, 0);
    }

    @Test
    void closedSessionKeepsExistingBehaviorPendingProductEligibilityDecision() {
        arrangeClaim(GameSessionState.CLOSED);
        when(winClaimRepository.existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot(
                TICKET_ID, BingoPatternType.LINE, 0)).thenThrow(new ValidationReached());

        assertThrows(ValidationReached.class, () -> submitClaim(new BingoUserPrincipal(player)));

        verify(winClaimRepository).existsByTicketIdAndClaimedPatternAndDrawSequenceSnapshot(
                TICKET_ID, BingoPatternType.LINE, 0);
    }

    @Test
    void nullSessionStateReturnsControlledDomainConflict() {
        ConflictException failure = assertThrows(ConflictException.class,
                () -> new WinClaimSessionValidator().validate(null));

        assertEquals("SESSION_STATE_INVALID", failure.getCode());
    }

    @Test
    void requestCannotSupplySessionStatusOrDrawHistory() {
        String[] components = Arrays.stream(WinClaimRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .toArray(String[]::new);

        assertArrayEquals(new String[] { "claimedPattern" }, components);
    }

    @Test
    void claimEndpointContractRemainsPlayerOnlyAndIdempotent() throws NoSuchMethodException {
        var method = PublicGameController.class.getDeclaredMethod("submitWinClaim", Long.class,
                BingoUserPrincipal.class, WinClaimRequest.class, String.class);

        assertEquals("hasRole('PLAYER')", method.getAnnotation(PreAuthorize.class).value());
        assertArrayEquals(new String[] { "/tickets/{id}/win-claims" }, method.getAnnotation(PostMapping.class).value());
        assertTrue(Arrays.stream(method.getParameters()[3].getAnnotations())
                .anyMatch(annotation -> annotation instanceof RequestHeader header
                        && header.value().equals("Idempotency-Key")));
    }

    private void submitClaim(BingoUserPrincipal principal) {
        service.submitWinClaim(TICKET_ID, principal, new WinClaimRequest(BingoPatternType.LINE), "unit-key");
    }

    private void arrangeClaim(GameSessionState authoritativeState) {
        player = mock(User.class);
        when(player.getId()).thenReturn(PLAYER_ID);
        when(player.hasRole(UserRole.PLAYER)).thenReturn(true);

        ticket = mock(Ticket.class);
        GameSession ticketSession = mock(GameSession.class);
        lockedSession = mock(GameSession.class);
        when(ticket.getSession()).thenReturn(ticketSession);
        when(ticketSession.getId()).thenReturn(SESSION_ID);
        when(ticketRepository.findByIdAndOwnerId(TICKET_ID, PLAYER_ID)).thenReturn(Optional.of(ticket));
        when(gameSessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(lockedSession));
        when(lockedSession.getState()).thenReturn(authoritativeState);
        when(requestHashService.hash(anyString(), anyString(), any())).thenReturn("request-hash");
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(6);
            return new IdempotentResult<>(201, supplier.get());
        }).when(idempotencyService).execute(anyLong(), anyString(), anyString(), anyString(), anyInt(),
                eq(WinClaimResponse.class), any(Supplier.class));
    }

    private static final class ValidationReached extends RuntimeException {
    }
}
