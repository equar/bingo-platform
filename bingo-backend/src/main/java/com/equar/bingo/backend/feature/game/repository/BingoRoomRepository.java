package com.equar.bingo.backend.feature.game.repository;

import com.equar.bingo.backend.feature.game.entity.BingoRoom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BingoRoomRepository extends JpaRepository<BingoRoom, Long> {

    List<BingoRoom> findAllByOrderByActiveDescNameAsc();

    List<BingoRoom> findByActiveTrueOrderByNameAsc();

    Optional<BingoRoom> findByIdAndActiveTrue(Long id);
}