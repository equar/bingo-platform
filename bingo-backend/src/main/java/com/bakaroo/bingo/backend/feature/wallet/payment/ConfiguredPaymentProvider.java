package com.bakaroo.bingo.backend.feature.wallet.payment;

import com.bakaroo.bingo.backend.feature.wallet.config.WalletProperties;
import org.springframework.stereotype.Component;

@Component
public class ConfiguredPaymentProvider implements PaymentProvider {

    private final WalletProperties walletProperties;

    public ConfiguredPaymentProvider(WalletProperties walletProperties) {
        this.walletProperties = walletProperties;
    }

    @Override
    public PaymentInstructions currentInstructions() {
        String businessAccount = walletProperties.businessAccount();
        if (businessAccount != null && businessAccount.isBlank()) {
            businessAccount = null;
        }

        return new PaymentInstructions(
                walletProperties.paymentProviderName(),
                businessAccount,
                walletProperties.paymentInstructions(),
                walletProperties.currency());
    }
}