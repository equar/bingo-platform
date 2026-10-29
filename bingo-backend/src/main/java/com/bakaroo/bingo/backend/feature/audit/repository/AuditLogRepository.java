package com.bakaroo.bingo.backend.feature.audit.repository;

import com.bakaroo.bingo.backend.feature.audit.entity.AuditLogEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository
        extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

    @Override
    @EntityGraph(attributePaths = "actor")
    Optional<AuditLogEntry> findById(Long id);
}
