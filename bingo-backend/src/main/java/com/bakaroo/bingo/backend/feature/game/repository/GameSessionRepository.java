package com.bakaroo.bingo.backend.feature.game.repository;

import com.bakaroo.bingo.backend.feature.game.entity.GameSession;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
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
                                        or lower(s.room.name) like concat('%', :query, '%'))
                                and (:sessionId is null or s.id = :sessionId)
                                and (:upcoming is null
                                        or (:upcoming = true and s.scheduledStartAt >= :now)
                                        or (:upcoming = false and s.scheduledStartAt < :now))
                        order by s.scheduledStartAt desc, s.id desc
                        """)
        Page<GameSession> searchAdminSessions(@Param("query") String query, @Param("sessionId") Long sessionId,
                        @Param("state") GameSessionState state, @Param("roomId") Long roomId,
                        @Param("upcoming") Boolean upcoming, @Param("now") Instant now, Pageable pageable);

        Optional<GameSession> findByIdAndStateIn(Long id, List<GameSessionState> states);

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