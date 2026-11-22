package com.bakaroo.bingo.backend.feature.game.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.game.websocket.GameWebSocketJwtChannelInterceptor.GameWebSocketTokenContext;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class GameWebSocketRevocationChannelInterceptorTest {

    private BingoUserDetailsService userDetailsService;
    private GameWebSocketJwtChannelInterceptor jwtChannelInterceptor;
    private GameWebSocketRevocationChannelInterceptor interceptor;
    private User player;
    private Message<byte[]> protectedDraw;

    @BeforeEach
    void setUp() {
        userDetailsService = org.mockito.Mockito.mock(BingoUserDetailsService.class);
        jwtChannelInterceptor = org.mockito.Mockito.mock(GameWebSocketJwtChannelInterceptor.class);
        interceptor = new GameWebSocketRevocationChannelInterceptor(userDetailsService, jwtChannelInterceptor);
        player = User.create("251911000001", "Player One", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.PLAYER), Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(player, "id", 1L);
        player.startPlayerSession("active-session", Instant.now().plusSeconds(3600));
        when(userDetailsService.loadById(1L)).thenReturn(new BingoUserPrincipal(player));
        when(jwtChannelInterceptor.tokenContextForSession("socket-session"))
                .thenReturn(new GameWebSocketTokenContext(
                        1L, player.getSecurityTokenVersion(), "active-session", Instant.now().plusSeconds(3600)));

        BingoUserPrincipal principal = new BingoUserPrincipal(player);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new GameWebSocketTokenContext(
                1L, player.getSecurityTokenVersion(), "active-session", Instant.now().plusSeconds(3600)));
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId("socket-session");
        accessor.setDestination("/topic/session/42/draws");
        accessor.setUser(authentication);
        protectedDraw = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void allowsLiveEventsWhileTokenAndPlayerSessionRemainCurrent() {
        assertThat(interceptor.preSend(protectedDraw, null)).isSameAs(protectedDraw);
    }

    @Test
    void dropsLiveEventsAfterTokenVersionRevocation() {
        player.revokeSecurityTokens();

        assertThat(interceptor.preSend(protectedDraw, null)).isNull();
    }

    @Test
    void dropsLiveEventsAfterPlayerSessionReplacement() {
        player.startPlayerSession("replacement-session", Instant.now().plusSeconds(3600));

        assertThat(interceptor.preSend(protectedDraw, null)).isNull();
    }

    @Test
    void dropsLiveEventsAfterTokenExpiry() {
        when(jwtChannelInterceptor.tokenContextForSession("socket-session"))
                .thenReturn(new GameWebSocketTokenContext(
                        1L, player.getSecurityTokenVersion(), "active-session", Instant.now().minusSeconds(1)));

        assertThat(interceptor.preSend(protectedDraw, null)).isNull();
    }
}
