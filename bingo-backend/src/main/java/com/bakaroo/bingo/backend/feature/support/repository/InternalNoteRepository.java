package com.bakaroo.bingo.backend.feature.support.repository;

import com.bakaroo.bingo.backend.feature.support.entity.InternalNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalNoteRepository extends JpaRepository<InternalNote, Long> {

    List<InternalNote> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}