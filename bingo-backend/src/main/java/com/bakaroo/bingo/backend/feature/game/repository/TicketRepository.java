package com.bakaroo.bingo.backend.feature.game.repository;

import com.bakaroo.bingo.backend.feature.game.entity.Ticket;
import com.bakaroo.bingo.backend.feature.game.dto.SessionTicketLookupResponse;
import com.bakaroo.bingo.backend.feature.game.dto.PlayerGameHistoryResponse;
import com.bakaroo.bingo.backend.feature.game.entity.GameSessionState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query(value = """
            select new com.bakaroo.bingo.backend.feature.game.dto.SessionTicketLookupResponse(
                t.id, t.session.id, t.owner.id, t.owner.displayName, t.owner.normalizedPhone,
                t.publicTicketCode, t.purchasedAt)
            from Ticket t
            where t.session.id = :sessionId
                and (
                    :query is null
                    or lower(t.owner.displayName) like concat('%', :query, '%')
                    or lower(t.publicTicketCode) like concat('%', :query, '%')
                    or (:phoneQuery is not null and t.owner.normalizedPhone like concat('%', :phoneQuery, '%'))
                    or (:ticketId is not null and t.id = :ticketId)
                )
            order by t.purchasedAt desc, t.id desc
            """,
            countQuery = """
            select count(t)
            from Ticket t
            where t.session.id = :sessionId
                and (
                    :query is null
                    or lower(t.owner.displayName) like concat('%', :query, '%')
                    or lower(t.publicTicketCode) like concat('%', :query, '%')
                    or (:phoneQuery is not null and t.owner.normalizedPhone like concat('%', :phoneQuery, '%'))
                    or (:ticketId is not null and t.id = :ticketId)
                )
            """)
    Page<SessionTicketLookupResponse> searchBySessionId(
            @Param("sessionId") Long sessionId,
            @Param("query") String query,
            @Param("phoneQuery") String phoneQuery,
            @Param("ticketId") Long ticketId,
            Pageable pageable);

    Page<Ticket> findBySessionIdAndOwnerId(Long sessionId, Long ownerId, Pageable pageable);

    Page<Ticket> findByOwnerIdOrderByPurchasedAtDesc(Long ownerId, Pageable pageable);

    @Query(value = """
            select new com.bakaroo.bingo.backend.feature.game.dto.PlayerGameHistoryResponse(
                t.id, t.session.id, t.session.room.name, t.session.sessionName, t.session.state,
                t.publicTicketCode, t.purchasedAt)
            from Ticket t
            where t.owner.id = :ownerId and t.session.state in :states
            order by t.purchasedAt desc, t.id desc
            """, countQuery = """
            select count(t) from Ticket t
            where t.owner.id = :ownerId and t.session.state in :states
            """)
    Page<PlayerGameHistoryResponse> findPlayerGameHistory(
            @Param("ownerId") Long ownerId,
            @Param("states") List<GameSessionState> states,
            Pageable pageable);

    List<Ticket> findBySessionIdAndOwnerIdOrderByPurchasedAtDesc(Long sessionId, Long ownerId);

    Optional<Ticket> findByIdAndOwnerId(Long id, Long ownerId);

    List<Ticket> findBySessionIdOrderByIdAsc(Long sessionId);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndOwnerId(Long sessionId, Long ownerId);

    long countByOwnerId(Long ownerId);

    @Query("select t.owner.id, count(t) from Ticket t where t.owner.id in :ownerIds group by t.owner.id")
    List<Object[]> countByOwnerIds(@Param("ownerIds") Collection<Long> ownerIds);
}
