package com.bakaroo.bingo.backend.feature.game.repository;

import com.bakaroo.bingo.backend.feature.game.entity.GameSession;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import com.bakaroo.bingo.backend.feature.admin.dto.AdminSessionSummaryResponse;
import com.bakaroo.bingo.backend.feature.support.entity.SupportLinkedEntityType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

        @Query("""
                        select s from GameSession s
                        where (:state is null or s.state = :state)
                                and (:roomId is null or s.room.id = :roomId)
                                and (:query is null
                                        or lower(s.sessionName) like concat('%', :query, '%')
                                        or lower(s.room.name) like concat('%', :query, '%')
                                        or (:sessionId is not null and s.id = :sessionId))
                                and (:upcoming is null
                                        or (:upcoming = true and s.scheduledStartAt >= :now)
                                        or (:upcoming = false and s.scheduledStartAt < :now))
                        order by s.scheduledStartAt desc, s.id desc
                        """)
        Page<GameSession> searchAdminSessions(@Param("query") String query, @Param("sessionId") Long sessionId,
                        @Param("state") GameSessionState state, @Param("roomId") Long roomId,
                        @Param("upcoming") Boolean upcoming, @Param("now") Instant now, Pageable pageable);

        Page<GameSession> findByScheduledStartAtGreaterThanEqualAndScheduledStartAtLessThanOrderByScheduledStartAtDescIdDesc(
                        Instant from, Instant to, Pageable pageable);

        @Query(value = """
                        select new com.bakaroo.bingo.backend.feature.admin.dto.AdminSessionSummaryResponse(
                                s.id, s.room.id, s.room.name, s.sessionName, s.state, s.scheduledStartAt,
                                s.startedAt, s.endedAt, s.ticketPriceSnapshot, s.currencySnapshot,
                                (select count(t) from Ticket t where t.session.id = s.id),
                                (select count(d) from DrawEvent d where d.session.id = s.id),
                                (select count(c) from WinClaim c where c.session.id = s.id),
                                (select count(st) from SupportTicket st
                                        where st.linkedEntityType = :linkedEntityType and st.linkedEntityId = s.id))
                        from GameSession s
                        where s.scheduledStartAt >= :from and s.scheduledStartAt < :to
                                and (:state is null or s.state = :state)
                                and (:query is null
                                        or lower(s.sessionName) like concat('%', :query, '%')
                                        or lower(s.room.name) like concat('%', :query, '%')
                                        or (:numericQuery is not null and s.id = :numericQuery))
                        order by s.scheduledStartAt desc, s.id desc
                        """,
                        countQuery = """
                        select count(s) from GameSession s
                        where s.scheduledStartAt >= :from and s.scheduledStartAt < :to
                                and (:state is null or s.state = :state)
                                and (:query is null
                                        or lower(s.sessionName) like concat('%', :query, '%')
                                        or lower(s.room.name) like concat('%', :query, '%')
                                        or (:numericQuery is not null and s.id = :numericQuery))
                        """)
        Page<AdminSessionSummaryResponse> reportAdminSessions(
                        @Param("from") Instant from,
                        @Param("to") Instant to,
                        @Param("query") String query,
                        @Param("numericQuery") Long numericQuery,
                        @Param("state") GameSessionState state,
                        @Param("linkedEntityType") SupportLinkedEntityType linkedEntityType,
                        Pageable pageable);

        Optional<GameSession> findByIdAndStateIn(Long id, List<GameSessionState> states);

        @Query("""
                        select s from GameSession s join fetch s.room r
                        where s.state = com.bakaroo.bingo.backend.feature.game.entity.GameSessionState.OPEN
                                and r.active = true and r.privateRoom = false
                        order by s.scheduledStartAt asc, s.id asc
                        """)
        List<GameSession> findOpenPublicRoomSessionsForAgents();

        List<GameSession> findByRoomIdOrderByScheduledStartAtDesc(Long roomId);

        List<GameSession> findByStateInAndStartedAtIsNotNullAndEndedAtIsNullOrderByScheduledStartAtAsc(
                        List<GameSessionState> states);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select s from GameSession s where s.id = :id")
        Optional<GameSession> findByIdForUpdate(@Param("id") Long id);

        @Query("select s from GameSession s where s.room.id = :roomId and s.scheduledStartAt = :scheduledStartAt")
        Optional<GameSession> findByRoomIdAndScheduledStartAt(@Param("roomId") Long roomId,
                        @Param("scheduledStartAt") Instant scheduledStartAt);
}
