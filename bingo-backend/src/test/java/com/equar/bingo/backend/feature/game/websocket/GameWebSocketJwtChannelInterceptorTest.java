package com.equar.bingo.backend.feature.game.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.equar.bingo.backend.config.JwtProperties;
import com.equar.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.equar.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.equar.bingo.backend.feature.auth.service.EthiopianPhoneNormalizer;
import com.equar.bingo.backend.feature.auth.service.JwtService;
import com.equar.bingo.backend.feature.user.entity.AccountStatus;
import com.equar.bingo.backend.feature.user.entity.User;
import com.equar.bingo.backend.feature.user.entity.UserRole;
import com.equar.bingo.backend.feature.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

class GameWebSocketJwtChannelInterceptorTest {

    UserRepository userRepository;
    EthiopianPhoneNormalizer phoneNormalizer;
    JwtService jwtService;
    BingoUserDetailsService userDetailsService;
    GameWebSocketJwtChannelInterceptor interceptor;

    User admin;

    @BeforeEach
    void setUp() {
        phoneNormalizer = new EthiopianPhoneNormalizer();
        jwtService = new JwtService(new JwtProperties("01234567890123456789012345678901", 3600L));
        userRepository = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[] { UserRepository.class },
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException("Repository should not be called in this test");
                });
        userDetailsService = new BingoUserDetailsService(userRepository, phoneNormalizer) {
            @Override
            public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String username) {
                return new BingoUserPrincipal(admin);
            }
        };
        interceptor = new GameWebSocketJwtChannelInterceptor(jwtService, userDetailsService);

        admin = User.create("251911000001", "Admin One", "hash", AccountStatus.ACTIVE, Set.of(UserRole.ADMIN),
                Instant.parse("2026-01-01T00:00:00Z"));
        admin.setEmail("admin@example.com");
    }

    @Test
    void authenticatesConnectFramesWithBearerToken() {
        String token = jwtService.generateToken(admin);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNotNull();
    }

    @Test
    void rejectsMissingOrInvalidAuthorizationHeaders() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, null));
    }
}