package com.bakaroo.bingo.backend.feature.game.websocket;

import com.bakaroo.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.auth.service.JwtService;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GameWebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final BingoUserDetailsService userDetailsService;

    public GameWebSocketJwtChannelInterceptor(JwtService jwtService, BingoUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Authentication authentication = authenticate(accessor.getNativeHeader("Authorization"));
            accessor.setUser(authentication);
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }
        return message;
    }

    private Authentication authenticate(List<String> authorizationHeaders) {
        if (authorizationHeaders == null || authorizationHeaders.isEmpty()) {
            throw new IllegalArgumentException("Missing Authorization header");
        }
        String authorization = authorizationHeaders.get(0);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing bearer token");
        }

        String token = authorization.substring(7);
        String subject = jwtService.extractSubject(token);
        BingoUserPrincipal principal = (BingoUserPrincipal) userDetailsService.loadUserByUsername(subject);
        if (!jwtService.isTokenValid(token, principal.getUsername()) || !principal.isEnabled()) {
            throw new IllegalArgumentException("Invalid websocket token");
        }

        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}