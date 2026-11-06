package com.bakaroo.bingo.backend.common.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

class EmailProviderConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(EmailProviderConfiguration.class);

    @Test
    void smtpUsesBootTransportWithoutGraphCredentials() {
        context.withPropertyValues("app.email.provider=smtp", "spring.mail.host=smtp.example.com")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(JavaMailSender.class);
                    assertThat(ctx.getBean(JavaMailSender.class)).isNotInstanceOf(GraphMailSender.class);
                });
    }

    @Test
    void graphWorksWithoutSmtpAndAcceptsExistingCredentialAliases() {
        context.withPropertyValues("app.email.provider=graph", "app.email.from=apps@bakaroo.com",
                "GRAPH_TENANT_ID=tenant.example", "GRAPH_CLIENT_ID=id", "GRAPH_CLIENT_SECRET=secret")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(JavaMailSender.class);
                    assertThat(ctx.getBean(JavaMailSender.class)).isInstanceOf(GraphMailSender.class);
                });
    }

    @Test
    void graphReplacesSmtpEvenWhenSmtpHostRemainsConfigured() {
        context.withPropertyValues("app.email.provider=graph", "spring.mail.host=smtp.example.com",
                "app.email.from=apps@bakaroo.com", "MICROSOFT_GRAPH_TENANT_ID=tenant.example",
                "MICROSOFT_GRAPH_CLIENT_ID=id", "MICROSOFT_GRAPH_CLIENT_SECRET=secret")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(JavaMailSender.class);
                    assertThat(ctx.getBean(JavaMailSender.class)).isInstanceOf(GraphMailSender.class);
                });
    }

    @Test
    void graphFailsStartupOnMissingCredentials() {
        context.withPropertyValues("app.email.provider=graph", "app.email.from=apps@bakaroo.com")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
