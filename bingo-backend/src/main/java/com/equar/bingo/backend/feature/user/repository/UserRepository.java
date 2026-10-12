package com.equar.bingo.backend.feature.user.repository;

import com.equar.bingo.backend.feature.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNormalizedPhone(String normalizedPhone);

    boolean existsByNormalizedPhone(String normalizedPhone);
}