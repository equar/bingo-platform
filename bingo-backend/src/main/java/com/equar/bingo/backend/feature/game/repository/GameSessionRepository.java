package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.GameSession;
import com.equar.bingo.backend.feature.game.entity.GameSessionState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findByIdAndStateIn(Long id, List<GameSessionState> states);

    List<GameSession> findByRoomIdOrderByScheduledStartAtDesc(Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GameSession s where s.id = :id")
    Optional<GameSession> findByIdForUpdate(@Param("id") Long id);

    @Query("select s from GameSession s where s.room.id = :roomId and s.scheduledStartAt = :scheduledStartAt")
    Optional<GameSession> findByRoomIdAndScheduledStartAt(@Param("roomId") Long roomId, @Param("scheduledStartAt") Instant scheduledStartAt);
}