package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.BingoRoom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BingoRoomRepository extends JpaRepository<BingoRoom, Long> {

    @Query("""
            select r from BingoRoom r
            where (:active is null or r.active = :active)
                and (:query is null
                    or lower(r.name) like concat('%', :query, '%')
                    or lower(coalesce(r.description, '')) like concat('%', :query, '%')
                    or lower(r.currency) like concat('%', :query, '%')
                    or lower(r.scheduleRule) like concat('%', :query, '%')
                                or lower(r.configuredPatternsJson) like concat('%', :query, '%')
                                or (:roomId is not null and r.id = :roomId))
            order by r.active desc, r.name asc
            """)
    Page<BingoRoom> searchAdminRooms(@Param("query") String query, @Param("roomId") Long roomId,
            @Param("active") Boolean active, Pageable pageable);

    List<BingoRoom> findAllByOrderByActiveDescNameAsc();

    @Query("select r from BingoRoom r where r.active=true and r.privateRoom=false order by r.name asc")
    List<BingoRoom> findByActiveTrueOrderByNameAsc();

    Optional<BingoRoom> findByIdAndActiveTrue(Long id);
}