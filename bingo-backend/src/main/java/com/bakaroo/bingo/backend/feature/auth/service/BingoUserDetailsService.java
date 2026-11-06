package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class BingoUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final EthiopianPhoneNormalizer phoneNormalizer;

    public BingoUserDetailsService(UserRepository userRepository, EthiopianPhoneNormalizer phoneNormalizer) {
        this.userRepository = userRepository;
        this.phoneNormalizer = phoneNormalizer;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByEmailIgnoreCase(username)
                .filter(user -> user.getNormalizedPhone() == null)
                .map(BingoUserPrincipal::new)
                .orElseGet(() -> loadByPhone(username));
    }

    private BingoUserPrincipal loadByPhone(String username) {
        String normalizedPhone = phoneNormalizer.normalize(username);
        return userRepository.findByNormalizedPhoneAndRole(normalizedPhone, UserRole.PLAYER)
                .map(BingoUserPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("User account not found"));
    }

    public BingoUserPrincipal loadById(Long userId) {
        return userRepository.findById(userId)
                .map(BingoUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User account not found"));
    }
}
