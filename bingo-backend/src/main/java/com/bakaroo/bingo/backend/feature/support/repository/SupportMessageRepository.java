package com.bakaroo.bingo.backend.feature.support.repository;

import com.bakaroo.bingo.backend.feature.support.entity.SupportMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    List<SupportMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}