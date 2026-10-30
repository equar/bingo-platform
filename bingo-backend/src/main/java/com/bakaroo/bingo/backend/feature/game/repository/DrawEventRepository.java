package com.bakaroo.bingo.backend.feature.game.repository;

import com.bakaroo.bingo.backend.feature.game.entity.DrawEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawEventRepository extends JpaRepository<DrawEvent, Long> {

    List<DrawEvent> findBySessionIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(Long sessionId, int sequenceNo);

    List<DrawEvent> findBySessionIdOrderBySequenceNoAsc(Long sessionId);

    Optional<DrawEvent> findTopBySessionIdOrderBySequenceNoDesc(Long sessionId);

    long countBySessionId(Long sessionId);
}