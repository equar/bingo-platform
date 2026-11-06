package com.bakaroo.bingo.backend.feature.audit.repository;

import com.bakaroo.bingo.backend.feature.audit.entity.AuditLogEntry;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository
        extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

    @EntityGraph(attributePaths = "actor")
    Page<AuditLogEntry> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Instant from, Instant to, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "actor")
    Page<AuditLogEntry> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    @Query(value = """
            select entry
            from AuditLogEntry entry
            left join entry.actor actor
            where (:query is null
                or lower(entry.action) like concat('%', :query, '%')
                or lower(entry.targetType) like concat('%', :query, '%')
                or lower(coalesce(entry.reason, '')) like concat('%', :query, '%')
                or lower(entry.operationId) like concat('%', :query, '%')
                or lower(coalesce(entry.metadataJson, '')) like concat('%', :query, '%')
                or lower(coalesce(actor.displayName, '')) like concat('%', :query, '%'))
              and (:auditLogId is null or entry.id = :auditLogId)
              and (:actorUserId is null or actor.id = :actorUserId)
              and (:action is null or lower(entry.action) = :action)
              and (:targetType is null or lower(entry.targetType) = :targetType)
              and (:targetId is null or entry.targetId = :targetId)
              and (:operationId is null or lower(entry.operationId) like concat('%', :operationId, '%'))
              and (:createdAfter is null or entry.createdAt >= :createdAfter)
              and (:createdBefore is null or entry.createdAt <= :createdBefore)
            order by entry.createdAt desc, entry.id desc
            """, countQuery = """
            select count(entry)
            from AuditLogEntry entry
            left join entry.actor actor
            where (:query is null
                or lower(entry.action) like concat('%', :query, '%')
                or lower(entry.targetType) like concat('%', :query, '%')
                or lower(coalesce(entry.reason, '')) like concat('%', :query, '%')
                or lower(entry.operationId) like concat('%', :query, '%')
                or lower(coalesce(entry.metadataJson, '')) like concat('%', :query, '%')
                or lower(coalesce(actor.displayName, '')) like concat('%', :query, '%'))
              and (:auditLogId is null or entry.id = :auditLogId)
              and (:actorUserId is null or actor.id = :actorUserId)
              and (:action is null or lower(entry.action) = :action)
              and (:targetType is null or lower(entry.targetType) = :targetType)
              and (:targetId is null or entry.targetId = :targetId)
              and (:operationId is null or lower(entry.operationId) like concat('%', :operationId, '%'))
              and (:createdAfter is null or entry.createdAt >= :createdAfter)
              and (:createdBefore is null or entry.createdAt <= :createdBefore)
            """)
    Page<AuditLogEntry> searchAuditLogs(
            @Param("query") String query,
            @Param("actorUserId") Long actorUserId,
            @Param("auditLogId") Long auditLogId,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("operationId") String operationId,
            @Param("createdAfter") Instant createdAfter,
            @Param("createdBefore") Instant createdBefore,
            Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "actor")
    Optional<AuditLogEntry> findById(Long id);
}
