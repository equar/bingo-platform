package com.equar.bingo.backend.feature.game.websocket;

import com.equar.bingo.backend.common.exception.ConflictException;
import com.equar.bingo.backend.common.exception.NotFoundException;
import com.equar.bingo.backend.feature.audit.service.AuditLogService;
import com.equar.bingo.backend.feature.game.config.GameProperties;
import com.equar.bingo.backend.feature.game.dto.DrawEventResponse;
import com.equar.bingo.backend.feature.game.entity.DrawEvent;
import com.equar.bingo.backend.feature.game.entity.GameSession;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import com.equar.bingo.backend.feature.game.repository.DrawEventRepository;
import com.equar.bingo.backend.feature.game.repository.GameSessionRepository;
import com.equar.bingo.backend.feature.user.entity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.security.SecureRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GameLiveDrawService {

    private static final List<Integer> BINGO_POOL = IntStream.rangeClosed(1, 75).boxed().toList();

    private final GameSessionRepository gameSessionRepository;
    private final DrawEventRepository drawEventRepository;
    private final GameProperties gameProperties;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public GameLiveDrawService(GameSessionRepository gameSessionRepository,
            DrawEventRepository drawEventRepository,
            GameProperties gameProperties,
            ApplicationEventPublisher applicationEventPublisher,
            AuditLogService auditLogService,
            PlatformTransactionManager transactionManager) {
        this.gameSessionRepository = gameSessionRepository;
        this.drawEventRepository = drawEventRepository;
        this.gameProperties = gameProperties;
        this.applicationEventPublisher = applicationEventPublisher;
        this.auditLogService = auditLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<DrawEventResponse> advanceDueDraws(Instant now) {
        List<DrawEventResponse> draws = new ArrayList<>();
        List<Long> sessionIds = gameSessionRepository
                .findByStateInAndStartedAtIsNotNullAndEndedAtIsNullOrderByScheduledStartAtAsc(
                        List.of(GameSessionState.STARTED, GameSessionState.DRAWING))
                .stream()
                .map(GameSession::getId)
                .toList();

        for (Long sessionId : sessionIds) {
            advanceSessionDrawIfDue(sessionId, now).ifPresent(draws::add);
        }
        return draws;
    }

    public Optional<DrawEventResponse> advanceSessionDrawIfDue(Long sessionId, Instant now) {
        try {
            return transactionTemplate.execute(status -> advanceSessionDrawInTransaction(sessionId, now, null, false));
        } catch (OptimisticLockingFailureException ex) {
            return Optional.empty();
        }
    }

    public DrawEventResponse triggerAutomaticDraw(Long sessionId, Instant now, User operator) {
        Optional<DrawEventResponse> draw;
        try {
            draw = transactionTemplate.execute(
                    status -> advanceSessionDrawInTransaction(sessionId, now, operator, true));
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException("CONCURRENT_DRAW_CONFLICT", "The session changed while drawing a number");
        }
        if (draw == null || draw.isEmpty()) {
            throw new ConflictException("DRAW_NOT_AVAILABLE", "No draw is available for this session");
        }
        return draw.get();
    }

    private Optional<DrawEventResponse> advanceSessionDrawInTransaction(Long sessionId, Instant now,
            User requestedBy, boolean operatorTriggered) {
        GameSession session = gameSessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session == null || session.getStartedAt() == null || session.getEndedAt() != null) {
            if (operatorTriggered) {
                if (session == null) {
                    throw new NotFoundException("SESSION_NOT_FOUND", "Game session not found");
                }
                throw new ConflictException("SESSION_NOT_DRAWING", "Draws can only be triggered for an active session");
            }
            return Optional.empty();
        }
        if (session.getState() != GameSessionState.STARTED && session.getState() != GameSessionState.DRAWING) {
            if (operatorTriggered) {
                throw new ConflictException("SESSION_NOT_DRAWING", "Draws can only be triggered for an active session");
            }
            return Optional.empty();
        }

        Instant anchor = drawEventRepository.findTopBySessionIdOrderBySequenceNoDesc(sessionId)
                .map(DrawEvent::getDrawnAt)
                .orElse(session.getStartedAt());
        if (anchor != null && anchor.plus(gameProperties.drawInterval()).isAfter(now)) {
            return Optional.empty();
        }

        List<Integer> remainingNumbers = remainingNumbers(sessionId);
        if (remainingNumbers.isEmpty()) {
            closeFinishedSession(session, now);
            return Optional.empty();
        }

        if (session.getState() == GameSessionState.STARTED) {
            session.setState(GameSessionState.DRAWING);
        }

        DrawEvent draw = new DrawEvent();
        draw.setSession(session);
        draw.setSequenceNo((int) drawEventRepository.countBySessionId(sessionId) + 1);
        draw.setNumberDrawn(pickNumber(remainingNumbers));
        draw.setOperationId(java.util.UUID.randomUUID().toString());
        draw.setDrawnBy(requestedBy);
        draw.setDrawnAt(now);
        draw = drawEventRepository.save(draw);

        if (remainingNumbers.size() == 1) {
            closeFinishedSession(session, now);
        }

        DrawEventResponse response = new DrawEventResponse(draw.getId(), session.getId(), draw.getSequenceNo(),
                draw.getNumberDrawn(), draw.getOperationId(), draw.getDrawnAt());
        auditLogService.record(requestedBy, "BINGO_NUMBER_DRAWN", "GAME_SESSION", session.getId(), null,
                draw.getOperationId(), Map.of(
                        "source", operatorTriggered ? "ADMIN_AUTOMATIC_TRIGGER" : "SCHEDULER",
                        "sequenceNo", draw.getSequenceNo(),
                        "numberDrawn", draw.getNumberDrawn()));
        applicationEventPublisher.publishEvent(new GameDrawRecordedEvent(response));
        return Optional.of(response);
    }

    private List<Integer> remainingNumbers(Long sessionId) {
        boolean[] seen = new boolean[75];
        drawEventRepository.findBySessionIdOrderBySequenceNoAsc(sessionId).forEach(draw -> {
            if (draw.getNumberDrawn() >= 1 && draw.getNumberDrawn() <= 75) {
                seen[draw.getNumberDrawn() - 1] = true;
            }
        });
        List<Integer> remaining = new ArrayList<>();
        for (Integer number : BINGO_POOL) {
            if (!seen[number - 1]) {
                remaining.add(number);
            }
        }
        return remaining;
    }

    private int pickNumber(List<Integer> remainingNumbers) {
        return remainingNumbers.get(secureRandom.nextInt(remainingNumbers.size()));
    }

    private void closeFinishedSession(GameSession session, Instant now) {
        if (session.getState() != GameSessionState.CLOSED
                && session.getState() != GameSessionState.SETTLED
                && session.getState() != GameSessionState.CANCELLED) {
            session.setState(GameSessionState.CLOSED);
            session.setEndedAt(now);
        }
    }
}
