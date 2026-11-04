package com.bakaroo.bingo.backend.feature.audit.repository;

import com.bakaroo.bingo.backend.feature.audit.entity.AuditLogEntry;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository
        extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

    Page<AuditLogEntry> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Instant from, Instant to, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "actor")
    Optional<AuditLogEntry> findById(Long id);
}
