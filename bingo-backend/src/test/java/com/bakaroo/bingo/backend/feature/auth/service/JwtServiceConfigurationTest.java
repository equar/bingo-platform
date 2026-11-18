package com.bakaroo.bingo.backend.feature.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bakaroo.bingo.backend.config.JwtProperties;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class JwtServiceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfiguration.class)
            .withPropertyValues("app.security.jwt.access-token-ttl-seconds=3600");

    @Test
    void applicationCannotStartWithoutSigningSecret() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void applicationCannotStartWithBlankOrWeakSigningSecret() {
        contextRunner.withPropertyValues("app.security.jwt.secret= ")
                .run(context -> assertThat(context).hasFailed());

        contextRunner.withPropertyValues("app.security.jwt.secret=too-short")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().getMessage()).doesNotContain("too-short");
                });
    }

    @Test
    void strongSecretStartsAndUsesHs256() {
        contextRunner.withPropertyValues("app.security.jwt.secret=01234567890123456789012345678901")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JwtService jwtService = context.getBean(JwtService.class);
                    User user = User.create(
                            "251911000001",
                            "Test user",
                            "hash",
                            AccountStatus.ACTIVE,
                            Set.of(UserRole.PLAYER),
                            Instant.now());
                    String token = jwtService.generateToken(user);
                    String header = new String(
                            Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                            StandardCharsets.UTF_8);
                    assertThat(header).contains("\"alg\":\"HS256\"");
                });
    }

    @Test
    void tokenVersionRevokesPreviouslyIssuedTokens() {
        contextRunner.withPropertyValues("app.security.jwt.secret=01234567890123456789012345678901")
                .run(context -> {
                    JwtService jwtService = context.getBean(JwtService.class);
                    User user = User.create("251911000001", "Test user", "hash", AccountStatus.ACTIVE,
                            Set.of(UserRole.ADMIN), Instant.now());
                    ReflectionTestUtils.setField(user, "id", 41L);

                    String token = jwtService.generateToken(user);
                    assertThat(jwtService.isTokenValidForUser(token, 41L, user.getSecurityTokenVersion())).isTrue();

                    user.revokeSecurityTokens();

                    assertThat(jwtService.isTokenValidForUser(token, 41L, user.getSecurityTokenVersion())).isFalse();
                });
    }

    @Test
    void agentTokensRemainValidUntilTheirUserTokenVersionIsRevoked() {
        contextRunner.withPropertyValues("app.security.jwt.secret=01234567890123456789012345678901")
                .run(context -> {
                    JwtService jwtService = context.getBean(JwtService.class);
                    User agent = User.create("251911000002", "Test agent", "hash", AccountStatus.ACTIVE,
                            Set.of(UserRole.AGENT), Instant.now());
                    ReflectionTestUtils.setField(agent, "id", 42L);

                    String token = jwtService.generateToken(agent);
                    assertThat(jwtService.isTokenValidForUser(token, 42L, agent.getSecurityTokenVersion())).isTrue();

                    agent.revokeSecurityTokens();

                    assertThat(jwtService.isTokenValidForUser(token, 42L, agent.getSecurityTokenVersion())).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtConfiguration {

        @Bean
        JwtService jwtService(JwtProperties properties) {
            return new JwtService(properties);
        }
    }
}
