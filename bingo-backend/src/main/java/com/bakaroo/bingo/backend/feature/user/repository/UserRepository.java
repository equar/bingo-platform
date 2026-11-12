package com.bakaroo.bingo.backend.feature.user.repository;

import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.entity.PlayerVerificationStatus;
import com.bakaroo.bingo.backend.feature.user.entity.PasswordResetEmailStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends JpaRepository<User, Long> {

    @Modifying
    @Transactional
    @Query("update User u set u.activationEmailStatus = :status, "
            + "u.activationEmailStatusUpdatedAt = :statusUpdatedAt where u.id = :id")
    int updateActivationEmailStatus(@Param("id") Long id,
            @Param("status") com.bakaroo.bingo.backend.feature.user.entity.ActivationEmailStatus status,
            @Param("statusUpdatedAt") Instant statusUpdatedAt);

    @Modifying
    @Transactional
    @Query("update User u set u.activationEmailStatus = :status, "
            + "u.activationEmailSentAt = :sentAt, "
            + "u.activationEmailStatusUpdatedAt = :sentAt where u.id = :id")
    int recordActivationEmailSent(@Param("id") Long id,
            @Param("status") com.bakaroo.bingo.backend.feature.user.entity.ActivationEmailStatus status,
            @Param("sentAt") Instant sentAt);

    @Modifying
    @Transactional
    @Query("update User u set u.passwordResetEmailStatus = :status, "
            + "u.passwordResetEmailStatusUpdatedAt = :statusUpdatedAt where u.id = :id")
    int updatePasswordResetEmailStatus(@Param("id") Long id,
            @Param("status") PasswordResetEmailStatus status,
            @Param("statusUpdatedAt") Instant statusUpdatedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByNormalizedPhone(String normalizedPhone);

    @Query("select distinct u from User u join u.roles role where u.normalizedPhone = :phone and role = :role")
    Optional<User> findByNormalizedPhoneAndRole(@Param("phone") String normalizedPhone, @Param("role") UserRole role);

    @Query("select count(distinct u) from User u join u.roles role "
            + "where u.normalizedPhone = :phone and role in :roles and (:excludeUserId is null or u.id <> :excludeUserId)")
    long countByPhoneAndAnyRoleExceptUser(
            @Param("phone") String normalizedPhone,
            @Param("roles") Collection<UserRole> roles,
            @Param("excludeUserId") Long excludeUserId);

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

    boolean existsByEmail(String email);
}
