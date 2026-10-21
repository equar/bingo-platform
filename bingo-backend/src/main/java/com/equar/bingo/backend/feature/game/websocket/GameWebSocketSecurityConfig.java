package com.equar.bingo.backend.feature.game.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class GameWebSocketSecurityConfig {

    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages.simpDestMatchers("/app/**").denyAll();
        messages.anyMessage().permitAll();
        return messages.build();
    }

    @Bean(name = "csrfChannelInterceptor")
    public ChannelInterceptor csrfChannelInterceptorForBearerAuthenticatedClients() {
        // STOMP CONNECT authentication uses the bearer JWT interceptor, not an HTTP-session CSRF token.
        return new ChannelInterceptor() {
        };
    }
}