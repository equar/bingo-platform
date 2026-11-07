package com.bakaroo.bingo.backend.feature.agent.repository;

import com.bakaroo.bingo.backend.feature.agent.entity.Agent;
import com.bakaroo.bingo.backend.feature.agent.entity.AgentStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    Optional<Agent> findByUserId(Long userId);

    Optional<Agent> findByUserNormalizedPhone(String normalizedPhone);

    boolean existsByUserId(Long userId);

    boolean existsByIdDocumentTypeIgnoreCaseAndIdDocumentNumberIgnoreCase(String idDocumentType,
            String idDocumentNumber);

    long countByStatus(AgentStatus status);

    @Query(value = """
            select a from Agent a join fetch a.user u
            where (:status is null or a.status = :status)
              and (:query is null or lower(u.displayName) like concat('%', :query, '%')
                   or lower(u.normalizedPhone) like concat('%', :query, '%')
                   or lower(a.city) like concat('%', :query, '%')
                   or lower(a.territory) like concat('%', :query, '%')
                   or lower(cast(a.status as string)) like concat('%', :query, '%'))
            """,
            countQuery = """
            select count(a) from Agent a join a.user u
            where (:status is null or a.status = :status)
              and (:query is null or lower(u.displayName) like concat('%', :query, '%')
                   or lower(u.normalizedPhone) like concat('%', :query, '%')
                   or lower(a.city) like concat('%', :query, '%')
                   or lower(a.territory) like concat('%', :query, '%')
                   or lower(cast(a.status as string)) like concat('%', :query, '%'))
            """)
    Page<Agent> searchAdminAgents(@Param("query") String query, @Param("status") AgentStatus status,
            Pageable pageable);
}
