package com.bakaroo.bingo.backend.feature.wallet.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.bakaroo.bingo.backend.feature.user.repository.UserRepository;
import com.bakaroo.bingo.backend.common.exception.BadRequestException;
import com.bakaroo.bingo.backend.feature.wallet.config.WalletProperties;
import com.bakaroo.bingo.backend.feature.wallet.entity.SignupRewardPolicy;
import com.bakaroo.bingo.backend.feature.wallet.entity.Wallet;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransaction;
import com.bakaroo.bingo.backend.feature.wallet.entity.WalletTransactionType;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardGrantRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.SignupRewardPolicyRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletRepository;
import com.bakaroo.bingo.backend.feature.wallet.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SignupRewardServiceTest {

    @Test
    void campaignStartUsesAddisBusinessDateAndAllowsMidnightToday() {
        SignupRewardService service = new SignupRewardService(mock(SignupRewardPolicyRepository.class),
                mock(SignupRewardGrantRepository.class), mock(WalletRepository.class),
                mock(WalletTransactionRepository.class),
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"),
                mock(UserRepository.class));
        ZoneId zone = ZoneId.of("Africa/Addis_Ababa");
        Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service,
                "validateCampaign", new BigDecimal("1.00"), "ETB", todayStart, null));
        Instant yesterdayStart = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant();
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class, () ->
                ReflectionTestUtils.invokeMethod(service, "validateCampaign", new BigDecimal("1.00"),
                        "ETB", yesterdayStart, null));
    }

    @Test
    void creditsReferralRewardToReferrerOnceForEachNewPlayer() {
        SignupRewardPolicyRepository policies = mock(SignupRewardPolicyRepository.class);
        SignupRewardGrantRepository grants = mock(SignupRewardGrantRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        UserRepository users = mock(UserRepository.class);
        Instant now = Instant.now();
        SignupRewardPolicy campaign = SignupRewardPolicy.create("Referral reward",
                SignupRewardPolicy.TriggerType.REFERRAL, true, new BigDecimal("15.00"), "ETB",
                now.minusSeconds(60), null, null, "invite", now);
        ReflectionTestUtils.setField(campaign, "id", 25L);
        when(policies.findAllByTriggerTypeOrderByCreatedAtDescIdDesc(SignupRewardPolicy.TriggerType.REFERRAL))
                .thenReturn(List.of(campaign));
        User referrer = User.create("251911000001", "Referrer", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        User referred = User.create("251911000002", "Referred", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        ReflectionTestUtils.setField(referrer, "id", 1L);
        ReflectionTestUtils.setField(referred, "id", 2L);
        referred.setReferralDetails("BREFFERED", referrer);
        Wallet wallet = Wallet.create(referrer, "ETB");
        wallet.setAvailableBalance(BigDecimal.ZERO.setScale(2));
        wallet.setPromotionalBalance(BigDecimal.ZERO.setScale(2));
        when(grants.existsByPolicyIdAndTriggerReferenceKey(25L, "REFERRAL-2")).thenReturn(false);
        when(wallets.findByOwnerIdForUpdate(1L)).thenReturn(Optional.of(wallet));
        when(wallets.save(wallet)).thenReturn(wallet);
        when(grants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SignupRewardService service = new SignupRewardService(policies, grants, wallets, transactions,
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"), users);

        service.grantReferralReward(referrer, referred);

        verify(grants).existsByPolicyIdAndTriggerReferenceKey(25L, "REFERRAL-2");
        ArgumentCaptor<WalletTransaction> entry = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactions).save(entry.capture());
        org.junit.jupiter.api.Assertions.assertEquals(WalletTransactionType.PROMOTIONAL_REWARD_CREDIT,
                entry.getValue().getTransactionType());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("15.00"), entry.getValue().getAmount());
        org.junit.jupiter.api.Assertions.assertEquals("Referral reward — invite", entry.getValue().getNote());
    }

    @Test
    void doesNotAwardReferralWhenNoActiveReferralCampaignExists() {
        SignupRewardPolicyRepository policies = mock(SignupRewardPolicyRepository.class);
        SignupRewardGrantRepository grants = mock(SignupRewardGrantRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(policies.findAllByTriggerTypeOrderByCreatedAtDescIdDesc(SignupRewardPolicy.TriggerType.REFERRAL))
                .thenReturn(List.of());
        SignupRewardService service = new SignupRewardService(policies, grants, wallets, transactions,
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"), users);
        Instant now = Instant.now();
        User referrer = User.create("251911000001", "Referrer", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        User referred = User.create("251911000002", "Referred", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);

        service.grantReferralReward(referrer, referred);

        verifyNoInteractions(policies, grants, wallets);
    }

    @Test
    void doesNotAwardReferralWhenRegistrationAttributionDoesNotMatchReferrer() {
        SignupRewardPolicyRepository policies = mock(SignupRewardPolicyRepository.class);
        SignupRewardGrantRepository grants = mock(SignupRewardGrantRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        UserRepository users = mock(UserRepository.class);
        Instant now = Instant.now();
        User referrer = User.create("251911000001", "Referrer", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        User otherReferrer = User.create("251911000003", "Other referrer", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        User referred = User.create("251911000002", "Referred", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        ReflectionTestUtils.setField(referrer, "id", 1L);
        ReflectionTestUtils.setField(otherReferrer, "id", 3L);
        ReflectionTestUtils.setField(referred, "id", 2L);
        referred.setReferralDetails("BREFFERED", otherReferrer);
        SignupRewardService service = new SignupRewardService(policies, grants, wallets, transactions,
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"), users);

        service.grantReferralReward(referrer, referred);

        verifyNoInteractions(policies, grants, wallets, transactions);
    }

    @Test
    void doesNotAwardWalletCreditCampaignBeforeVerifiedDepositsReachThreshold() {
        SignupRewardPolicyRepository policies = mock(SignupRewardPolicyRepository.class);
        SignupRewardGrantRepository grants = mock(SignupRewardGrantRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        UserRepository users = mock(UserRepository.class);
        Instant now = Instant.now();
        SignupRewardPolicy campaign = SignupRewardPolicy.create("Wallet credit reward",
                SignupRewardPolicy.TriggerType.WALLET_CREDIT, true, new BigDecimal("10.00"),
                new BigDecimal("100.00"), "ETB", now.minusSeconds(60), null, null, "threshold", now);
        when(policies.findAllByTriggerTypeOrderByCreatedAtDescIdDesc(SignupRewardPolicy.TriggerType.WALLET_CREDIT))
                .thenReturn(List.of(campaign));
        when(transactions.sumEtbDepositCreditsPostedInCampaignWindow(null, campaign.getEffectiveFrom(),
                campaign.getEffectiveUntil())).thenReturn(new BigDecimal("99.99"));
        SignupRewardService service = new SignupRewardService(policies, grants, wallets, transactions,
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"), users);
        User player = User.create("251911000001", "Player", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);

        service.grantWalletCreditReward(player);

        verify(transactions).sumEtbDepositCreditsPostedInCampaignWindow(null, campaign.getEffectiveFrom(),
                campaign.getEffectiveUntil());
        verifyNoInteractions(grants, wallets);
    }

    @Test
    void grantsWalletCreditRewardOnceWhenVerifiedDepositsReachThreshold() {
        SignupRewardPolicyRepository policies = mock(SignupRewardPolicyRepository.class);
        SignupRewardGrantRepository grants = mock(SignupRewardGrantRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        UserRepository users = mock(UserRepository.class);
        Instant now = Instant.now();
        SignupRewardPolicy campaign = SignupRewardPolicy.create("Wallet credit reward",
                SignupRewardPolicy.TriggerType.WALLET_CREDIT, true, new BigDecimal("10.00"),
                new BigDecimal("100.00"), "ETB", now.minusSeconds(60), null, null, null, now);
        ReflectionTestUtils.setField(campaign, "id", 26L);
        when(policies.findAllByTriggerTypeOrderByCreatedAtDescIdDesc(SignupRewardPolicy.TriggerType.WALLET_CREDIT))
                .thenReturn(List.of(campaign));
        User player = User.create("251911000001", "Player", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        ReflectionTestUtils.setField(player, "id", 3L);
        when(transactions.sumEtbDepositCreditsPostedInCampaignWindow(3L, campaign.getEffectiveFrom(),
                campaign.getEffectiveUntil())).thenReturn(new BigDecimal("100.00"));
        when(grants.existsByPolicyIdAndTriggerReferenceKey(26L, "WALLET-CREDIT-3")).thenReturn(false);
        Wallet wallet = Wallet.create(player, "ETB");
        wallet.setAvailableBalance(BigDecimal.ZERO.setScale(2));
        wallet.setPromotionalBalance(BigDecimal.ZERO.setScale(2));
        when(wallets.findByOwnerIdForUpdate(3L)).thenReturn(Optional.of(wallet));
        when(wallets.save(wallet)).thenReturn(wallet);
        when(grants.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SignupRewardService service = new SignupRewardService(policies, grants, wallets, transactions,
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"), users);

        service.grantWalletCreditReward(player);

        verify(transactions).sumEtbDepositCreditsPostedInCampaignWindow(3L, campaign.getEffectiveFrom(),
                campaign.getEffectiveUntil());
        verify(grants).existsByPolicyIdAndTriggerReferenceKey(26L, "WALLET-CREDIT-3");
        ArgumentCaptor<WalletTransaction> entry = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactions).save(entry.capture());
        org.junit.jupiter.api.Assertions.assertEquals(WalletTransactionType.PROMOTIONAL_REWARD_CREDIT,
                entry.getValue().getTransactionType());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("10.00"), entry.getValue().getAmount());
        org.junit.jupiter.api.Assertions.assertEquals("Wallet credit reward", entry.getValue().getNote());
    }

    @Test
    void fallsBackPastDisabledFutureAndExpiredSignupCampaigns() {
        SignupRewardPolicyRepository policies = mock(SignupRewardPolicyRepository.class);
        SignupRewardGrantRepository grants = mock(SignupRewardGrantRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        UserRepository users = mock(UserRepository.class);
        Instant now = Instant.now();
        SignupRewardPolicy expiredPolicy = SignupRewardPolicy.create(true, new BigDecimal("25.00"), "ETB",
                now.minusSeconds(7200), now.minusSeconds(3600), null, "Expired policy", now);
        SignupRewardPolicy disabledPolicy = SignupRewardPolicy.create(false, new BigDecimal("25.00"), "ETB",
                now.minusSeconds(7200), null, null, "Disabled policy", now.minusSeconds(1));
        SignupRewardPolicy futurePolicy = SignupRewardPolicy.create(true, new BigDecimal("25.00"), "ETB",
                now.plusSeconds(3600), null, null, "Future policy", now.minusSeconds(2));
        SignupRewardPolicy olderActivePolicy = SignupRewardPolicy.create(true, new BigDecimal("5.00"), "ETB",
                now.minusSeconds(7200), null, null, "Active fallback", now.minusSeconds(3));
        ReflectionTestUtils.setField(olderActivePolicy, "id", 91L);
        when(policies.findAllByTriggerTypeOrderByCreatedAtDescIdDesc(SignupRewardPolicy.TriggerType.SIGNUP))
                .thenReturn(List.of(expiredPolicy, disabledPolicy, futurePolicy, olderActivePolicy));
        User player = User.create("251911000001", "Player", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        ReflectionTestUtils.setField(player, "id", 4L);
        Wallet wallet = Wallet.create(player, "ETB");
        when(wallets.findByOwnerIdForUpdate(4L)).thenReturn(Optional.of(wallet));
        when(grants.existsByPolicyIdAndTriggerReferenceKey(91L, "PLAYER-4")).thenReturn(true);
        SignupRewardService service = new SignupRewardService(policies, grants, wallets, transactions,
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"), users);

        service.grantForNewPlayer(player);

        verify(policies).findAllByTriggerTypeOrderByCreatedAtDescIdDesc(SignupRewardPolicy.TriggerType.SIGNUP);
        verify(grants).existsByPolicyIdAndTriggerReferenceKey(91L, "PLAYER-4");
    }

    @Test
    void doesNotFallThroughWhenNewestEligibleCampaignWasAlreadyAwarded() {
        SignupRewardPolicyRepository policies = mock(SignupRewardPolicyRepository.class);
        SignupRewardGrantRepository grants = mock(SignupRewardGrantRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
        Instant now = Instant.now();
        SignupRewardPolicy newest = SignupRewardPolicy.create(true, new BigDecimal("25.00"), "ETB",
                now.minusSeconds(60), null, null, "newest", now);
        SignupRewardPolicy older = SignupRewardPolicy.create(true, new BigDecimal("5.00"), "ETB",
                now.minusSeconds(120), null, null, "older", now.minusSeconds(1));
        ReflectionTestUtils.setField(newest, "id", 92L);
        ReflectionTestUtils.setField(older, "id", 91L);
        when(policies.findAllByTriggerTypeOrderByCreatedAtDescIdDesc(SignupRewardPolicy.TriggerType.SIGNUP))
                .thenReturn(List.of(newest, older));
        User player = User.create("251911000001", "Player", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), now);
        ReflectionTestUtils.setField(player, "id", 4L);
        when(grants.existsByPolicyIdAndTriggerReferenceKey(92L, "PLAYER-4")).thenReturn(true);
        SignupRewardService service = new SignupRewardService(policies, grants, wallets, transactions,
                new WalletProperties("ETB", BigDecimal.ONE, "Test", null, "Test instructions"),
                mock(UserRepository.class));

        service.grantForNewPlayer(player);

        verify(grants).existsByPolicyIdAndTriggerReferenceKey(92L, "PLAYER-4");
        org.mockito.Mockito.verifyNoMoreInteractions(grants);
        org.mockito.Mockito.verifyNoInteractions(wallets, transactions);
    }
}
