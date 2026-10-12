package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.DrawEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawEventRepository extends JpaRepository<DrawEvent, Long> {

    List<DrawEvent> findBySessionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(Long sessionId, int sequenceNo);

    List<DrawEvent> findBySessionIdOrderBySequenceNoAsc(Long sessionId);

    long countBySessionId(Long sessionId);
}