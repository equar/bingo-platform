package com.bakaroo.bingo.backend.feature.game.websocket;

import com.bakaroo.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.game.websocket.GameWebSocketJwtChannelInterceptor.GameWebSocketTokenContext;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Drops protected live-game events for sockets whose access token was revoked,
 * expired, or replaced after the STOMP CONNECT handshake.
 */
@Component
public class GameWebSocketRevocationChannelInterceptor implements ChannelInterceptor {

    private static final Pattern SESSION_DRAWS = Pattern.compile("/topic/session/[0-9]+/draws");

    private final BingoUserDetailsService userDetailsService;
    private final GameWebSocketJwtChannelInterceptor jwtChannelInterceptor;

    public GameWebSocketRevocationChannelInterceptor(BingoUserDetailsService userDetailsService,
            GameWebSocketJwtChannelInterceptor jwtChannelInterceptor) {
        this.userDetailsService = userDetailsService;
        this.jwtChannelInterceptor = jwtChannelInterceptor;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        String destination = accessor.getDestination();
        if (!isProtectedDestination(destination)) {
            return message;
        }

        GameWebSocketTokenContext tokenContext = jwtChannelInterceptor.tokenContextForSession(accessor.getSessionId());
        if (tokenContext == null || !tokenContext.expiresAt().isAfter(Instant.now())) {
            return null;
        }

        try {
            BingoUserPrincipal currentPrincipal = userDetailsService.loadById(tokenContext.userId());
            User currentUser = currentPrincipal.getUser();
            if (!currentUser.getId().equals(tokenContext.userId())
                    || currentUser.getSecurityTokenVersion() != tokenContext.tokenVersion()
                    || !currentPrincipal.isEnabled()
                    || !currentPrincipal.isAccountNonLocked()) {
                return null;
            }
            if (currentUser.hasRole(UserRole.PLAYER)
                    && (tokenContext.playerSessionId() == null
                            || !tokenContext.playerSessionId().equals(currentUser.getActivePlayerSessionId())
                            || currentUser.getActivePlayerSessionExpiresAt() == null
                            || !currentUser.getActivePlayerSessionExpiresAt().isAfter(Instant.now()))) {
                return null;
            }
            return message;
        } catch (RuntimeException revokedOrMissingUser) {
            return null;
        }
    }

    private static boolean isProtectedDestination(String destination) {
        return "/topic/sessions".equals(destination)
                || (destination != null && SESSION_DRAWS.matcher(destination).matches());
    }
}
