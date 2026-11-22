package com.bakaroo.bingo.backend.feature.game.websocket;

import com.bakaroo.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.auth.service.JwtService;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.util.StringUtils;

@Component
public class GameWebSocketJwtChannelInterceptor implements ChannelInterceptor {

    private final com.bakaroo.bingo.backend.feature.community.service.GameAccessService gameAccess;
    private final JwtService jwtService;
    private final BingoUserDetailsService userDetailsService;
    private final ConcurrentMap<String, GameWebSocketTokenContext> sessionTokenContexts = new ConcurrentHashMap<>();

    public GameWebSocketJwtChannelInterceptor(JwtService jwtService, BingoUserDetailsService userDetailsService,
            com.bakaroo.bingo.backend.feature.community.service.GameAccessService gameAccess) {
        this.gameAccess = gameAccess;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor original = org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompHeaderAccessor accessor = original != null && original.isMutable() ? original : StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Authentication authentication = authenticate(accessor.getNativeHeader("Authorization"));
            accessor.setUser(authentication);
            if (accessor.getSessionId() != null
                    && authentication instanceof UsernamePasswordAuthenticationToken token
                    && token.getDetails() instanceof GameWebSocketTokenContext tokenContext) {
                sessionTokenContexts.put(accessor.getSessionId(), tokenContext);
            }
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }
        if (StompCommand.DISCONNECT.equals(accessor.getCommand()) && accessor.getSessionId() != null) {
            sessionTokenContexts.remove(accessor.getSessionId());
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (!(accessor.getUser() instanceof Authentication auth)
                    || !(auth.getPrincipal() instanceof BingoUserPrincipal principal)) {
                throw new IllegalArgumentException("Unsupported or unauthenticated subscription");
            }
            if ("/topic/sessions".equals(destination)) {
                return message;
            }
            if (destination == null || !destination.matches("/topic/session/[0-9]+/draws")) {
                throw new IllegalArgumentException("Unsupported or unauthenticated subscription");
            }
            Long sessionId = Long.valueOf(destination.split("/")[3]);
            gameAccess.requireSessionAccess(sessionId, principal);
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
        Long userId = jwtService.extractUserId(token);
        BingoUserPrincipal principal = userDetailsService.loadById(userId);
        boolean validToken = jwtService.isTokenValidForUser(token, principal.getUser().getId(),
                principal.getUser().getSecurityTokenVersion());
        if (validToken && principal.getUser().hasRole(UserRole.PLAYER)) {
            String tokenSessionId = jwtService.extractPlayerSessionId(token);
            validToken = tokenSessionId != null
                    && tokenSessionId.equals(principal.getUser().getActivePlayerSessionId());
        }
        if (!validToken || !principal.isEnabled() || !principal.isAccountNonLocked()) {
            throw new IllegalArgumentException("Invalid websocket token");
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new GameWebSocketTokenContext(
                userId,
                jwtService.extractTokenVersion(token),
                jwtService.extractPlayerSessionId(token),
                jwtService.extractExpiration(token)));
        return authentication;
    }

    record GameWebSocketTokenContext(Long userId, long tokenVersion, String playerSessionId, Instant expiresAt) { }

    GameWebSocketTokenContext tokenContextForSession(String sessionId) {
        return sessionId == null ? null : sessionTokenContexts.get(sessionId);
    }

    @EventListener
    void removeDisconnectedSession(SessionDisconnectEvent event) {
        sessionTokenContexts.remove(event.getSessionId());
    }
}
