package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.feature.user.repository.UserRepository;
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
        String normalizedPhone = phoneNormalizer.normalize(username);
        return userRepository.findByNormalizedPhone(normalizedPhone)
                .map(BingoUserPrincipal::new)
            .orElseThrow(() -> new UsernameNotFoundException("User account not found"));
    }
}