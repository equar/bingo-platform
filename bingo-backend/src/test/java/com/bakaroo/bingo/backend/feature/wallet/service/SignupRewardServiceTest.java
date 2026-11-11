package com.bakaroo.bingo.backend.feature.wallet.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.feature.wallet.config.WalletProperties;
import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardGrantRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardPolicyRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SignupRewardServiceTest {

    @Test
    void doesNotFallBackToOlderActiveSignupPolicyWhenLatestPolicyExpired() {
        SignupRewardPolicyRepository policies = mock(SignupRewardPolicyRepository.class);
        SignupRewardGrantRepository grants = mock(SignupRewardGrantRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        UserRepository users = mock(UserRepository.class);
        Instant now = Instant.now();
        SignupRewardPolicy latestPolicy = SignupRewardPolicy.create(true, new BigDecimal("25.00"), "ETB",
                now.minusSeconds(7200), now.minusSeconds(3600), null, "Expired policy", now);
        when(policies.findFirstByTriggerTypeOrderByCreatedAtDesc(SignupRewardPolicy.TriggerType.SIGNUP))
                .thenReturn(Optional.of(latestPolicy));
        SignupRewardService service = new SignupRewardService(policies, grants, wallets, transactions,
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"), users);
        User player = User.create("251911000001", "Player", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);

        service.grantForNewPlayer(player);

        verify(policies).findFirstByTriggerTypeOrderByCreatedAtDesc(SignupRewardPolicy.TriggerType.SIGNUP);
        verifyNoInteractions(grants, wallets, transactions);
    }
}
