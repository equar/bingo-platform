package com.bakaroo.bingo.backend.feature.admin.service;

import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerStatusResponse;
import com.bakaroo.bingo.backend.feature.admin.dto.PlayerStatusUpdateRequest;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminPlayerAccountService {

    private final UserRepository userRepository;

    public AdminPlayerAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public PlayerStatusResponse updatePlayerStatus(Long playerId, PlayerStatusUpdateRequest request) {
        User player = userRepository.findById(playerId)
                .filter(user -> user.getRoles().equals(Set.of(UserRole.PLAYER)))
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND", "Player account not found"));
        player.setStatus(request.status());
        User saved = userRepository.save(player);
        return new PlayerStatusResponse(saved.getId(), saved.getStatus(), saved.getUpdatedAt());
    }
}
