package com.bakaroo.bingo.backend.feature.game.service;

import com.bakaroo.bingo.backend.feature.game.config.GamePolicyProperties;
import com.bakaroo.bingo.backend.feature.game.entity.BingoRoom;
import com.bakaroo.bingo.backend.feature.game.entity.GameSession;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.game.repository.BingoRoomRepository;
import com.bakaroo.bingo.backend.feature.game.repository.GameSessionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomSessionCadenceService {
    private static final DateTimeFormatter SESSION_NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    private final BingoRoomRepository rooms;
    private final GameSessionRepository sessions;
    private final GamePolicyProperties policy;

    public RoomSessionCadenceService(BingoRoomRepository rooms, GameSessionRepository sessions,
            GamePolicyProperties policy) {
        this.rooms = rooms;
        this.sessions = sessions;
        this.policy = policy;
    }

    @Transactional
    public void ensureNextSession(Long roomId) {
        BingoRoom room = rooms.findByIdForUpdate(roomId).orElse(null);
        if (room == null || !room.isActive()) return;
        Instant now = Instant.now();
        if (sessions.existsFutureNonCancelledSession(roomId, now, null)) return;
        nextTime(room, now, now).ifPresent(scheduled -> sessions.save(createSession(room, scheduled)));
    }

    @Transactional
    public void scheduleAfterSuccessfulCompletion(GameSession completed, Instant completedAt) {
        if (!completed.isAutoScheduled()) return;
        Long roomId = completed.getRoom().getId();
        BingoRoom room = rooms.findByIdForUpdate(roomId).orElse(null);
        if (room == null || !room.isActive()
                || sessions.existsFutureNonCancelledSession(roomId, completedAt, completed.getId())) return;
        nextTime(room, completed.getScheduledStartAt(), completedAt)
                .ifPresent(scheduled -> sessions.save(createSession(room, scheduled)));
    }

    private GameSession createSession(BingoRoom room, Instant scheduled) {
        GameSession session = new GameSession();
        session.setRoom(room);
        session.setSessionName(sessionName(room, scheduled));
        session.setState(GameSessionState.OPEN);
        session.setScheduledStartAt(scheduled);
        session.setAutoScheduled(true);
        session.setTicketPriceSnapshot(room.getTicketPrice());
        session.setCurrencySnapshot(room.getCurrency());
        session.setSessionCapacitySnapshot(room.getSessionCapacity());
        session.setMaxTicketsPerPlayerSnapshot(room.getMaxTicketsPerPlayer());
        session.setTicketSalesCloseOnSessionStart(room.isTicketSalesCloseOnSessionStart());
        session.setAllowMultipleWinnersPerPrize(room.isAllowMultipleWinnersPerPrize());
        session.setPatternsSnapshotJson(room.getConfiguredPatternsJson());
        session.setPrizesSnapshotJson(room.getConfiguredPrizesJson());
        session.setMinimumHouseMarginBps(policy.minimumHouseMarginBps());
        session.setSessionCostReserve(policy.sessionCostReserve());
        session.setClaimWindowSeconds(policy.claimWindow().toSeconds());
        return session;
    }

    private Optional<Instant> nextTime(BingoRoom room, Instant anchor, Instant after) {
        Optional<Cadence> cadence = Cadence.from(room.getScheduleRule());
        if (cadence.isEmpty()) return Optional.empty();
        ZoneId zone = ZoneId.of(room.getTimezone());
        ZonedDateTime zonedAnchor = anchor.atZone(zone);
        int occurrence = 1;
        ZonedDateTime next = cadence.get().occurrence(zonedAnchor, occurrence);
        while (!next.toInstant().isAfter(after)) {
            next = cadence.get().occurrence(zonedAnchor, ++occurrence);
        }
        return Optional.of(next.toInstant().truncatedTo(ChronoUnit.SECONDS));
    }

    private String sessionName(BingoRoom room, Instant scheduled) {
        String suffix = " - " + SESSION_NAME_TIME.format(scheduled.atZone(ZoneId.of(room.getTimezone())));
        String roomName = room.getName();
        int roomNameLength = Math.max(1, 120 - suffix.length());
        if (roomName.length() > roomNameLength) roomName = roomName.substring(0, roomNameLength);
        return roomName + suffix;
    }

    private enum Cadence {
        HOURLY {
            @Override ZonedDateTime occurrence(ZonedDateTime anchor, int index) { return anchor.plusHours(index); }
        },
        DAILY {
            @Override ZonedDateTime occurrence(ZonedDateTime anchor, int index) { return anchor.plusDays(index); }
        },
        WEEKLY {
            @Override ZonedDateTime occurrence(ZonedDateTime anchor, int index) { return anchor.plusWeeks(index); }
        },
        BIWEEKLY {
            @Override ZonedDateTime occurrence(ZonedDateTime anchor, int index) { return anchor.plusWeeks(2L * index); }
        },
        MONTHLY {
            @Override ZonedDateTime occurrence(ZonedDateTime anchor, int index) { return anchor.plusMonths(index); }
        };

        abstract ZonedDateTime occurrence(ZonedDateTime anchor, int index);

        static Optional<Cadence> from(String value) {
            if (value == null) return Optional.empty();
            try {
                return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }
}
