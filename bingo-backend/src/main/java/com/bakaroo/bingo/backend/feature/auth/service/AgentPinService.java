package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.common.exception.NotFoundException;
import com.bakaroo.bingo.backend.feature.auth.dto.SetAgentPinRequest;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentPinService {

    private final UserRepository userRepository;
    private final PasswordEncoder agentPinEncoder;

    public AgentPinService(UserRepository userRepository,
            @Qualifier("agentPinEncoder") PasswordEncoder agentPinEncoder) {
        this.userRepository = userRepository;
        this.agentPinEncoder = agentPinEncoder;
    }

    @Transactional
    public void setAgentPin(Long userId, SetAgentPinRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User account not found"));

        if (!user.hasRole(UserRole.AGENT)) {
            throw new BadRequestException("NOT_AN_AGENT", "Agent PINs can only be set for agent accounts");
        }

        user.setAgentPinHash(agentPinEncoder.encode(request.pin()));
        userRepository.save(user);
    }
}