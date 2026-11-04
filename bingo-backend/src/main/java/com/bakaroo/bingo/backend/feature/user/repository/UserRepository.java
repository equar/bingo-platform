package com.bakaroo.bingo.backend.feature.user.repository;

import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByNormalizedPhone(String normalizedPhone);

    java.util.List<User> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByAccountActivationTokenHash(String accountActivationTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.accountActivationTokenHash = :tokenHash")
    Optional<User> findByAccountActivationTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Query(value = "select distinct u from User u join u.roles role where role in :roles",
            countQuery = "select count(distinct u) from User u join u.roles role where role in :roles")
    Page<User> findByAnyRole(@Param("roles") Collection<UserRole> roles, Pageable pageable);

    @Query(value = """
            select distinct u from User u join u.roles role
            where role = :role
                and not exists (
                    select other from User other join other.roles otherRole
                    where other.id = u.id and otherRole <> :role
                )
                and (:verificationStatus is null or u.playerVerificationStatus = :verificationStatus)
                and (:search is null or :search = ''
                    or lower(u.displayName) like concat('%', :search, '%')
                    or lower(u.email) like concat('%', :search, '%')
                    or lower(u.normalizedPhone) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(distinct u) from User u join u.roles role
            where role = :role
                and not exists (
                    select other from User other join other.roles otherRole
                    where other.id = u.id and otherRole <> :role
                )
                and (:verificationStatus is null or u.playerVerificationStatus = :verificationStatus)
                and (:search is null or :search = ''
                    or lower(u.displayName) like concat('%', :search, '%')
                    or lower(u.email) like concat('%', :search, '%')
                    or lower(u.normalizedPhone) like concat('%', :search, '%'))
            """)
    Page<User> findPlayerVerificationQueue(
            @Param("role") UserRole role,
            @Param("verificationStatus") PlayerVerificationStatus verificationStatus,
            @Param("search") String search,
            Pageable pageable);

        @Query(value = """
                        select distinct u from User u
                        join u.roles role
                        where role in :roles
                            and (:status is null or u.status = :status)
                            and (
                                :search is null or :search = ''
                                or lower(u.displayName) like concat('%', :search, '%')
                                or lower(u.email) like concat('%', :search, '%')
                                or lower(u.normalizedPhone) like concat('%', :search, '%')
                            )
                        """,
                        countQuery = """
                        select count(distinct u) from User u
                        join u.roles role
                        where role in :roles
                            and (:status is null or u.status = :status)
                            and (
                                :search is null or :search = ''
                                or lower(u.displayName) like concat('%', :search, '%')
                                or lower(u.email) like concat('%', :search, '%')
                                or lower(u.normalizedPhone) like concat('%', :search, '%')
                            )
                        """)
        Page<User> findAdminUsers(
                        @Param("roles") Collection<UserRole> roles,
                        @Param("search") String search,
                        @Param("status") AccountStatus status,
                        Pageable pageable);

    boolean existsByNormalizedPhone(String normalizedPhone);

    boolean existsByEmail(String email);
}
