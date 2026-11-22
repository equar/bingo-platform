package com.bakaroo.bingo.backend.feature.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bakaroo.bingo.backend.feature.game.controller.AdminGameController;
import com.bakaroo.bingo.backend.feature.game.repository.GameSessionRepository;
import com.bakaroo.bingo.backend.feature.game.repository.TicketRepository;
import com.bakaroo.bingo.backend.feature.game.service.BingoGameService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminGameAuthorizationTest {

    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void supportCannotReadAdminGameSessionOrTicketAtApiAndServiceBoundaries() {
        authenticate("ROLE_SUPPORT");
        AdminGameController controller = context.getBean(AdminGameController.class);
        BingoGameService service = context.getBean(BingoGameService.class);
        GameSessionRepository sessions = context.getBean(GameSessionRepository.class);
        TicketRepository tickets = context.getBean(TicketRepository.class);

        when(sessions.existsById(17L)).thenReturn(true);
        when(tickets.findByOwnerIdOrderByPurchasedAtDesc(88L, Pageable.unpaged())).thenReturn(Page.empty());
        when(tickets.searchBySessionId(17L, null, null, null, Pageable.unpaged())).thenReturn(Page.empty());

        // Support can use the scoped player/session ticket lookup workflows.
        assertThat(service.getAdminPlayerTickets(88L, Pageable.unpaged()).getContent()).isEmpty();
        assertThat(service.getAdminSessionTickets(17L, null, Pageable.unpaged()).getContent()).isEmpty();

        // Broad Admin game record endpoints remain Admin-only.
        assertThatThrownBy(() -> controller.session(17L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.ticket(23L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getSession(17L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAdminTicket(23L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAdminWinClaim(41L)).isInstanceOf(AccessDeniedException.class);

        verify(sessions, never()).findById(17L);
        verify(tickets, never()).findById(23L);
    }

    @Test
    void adminPassesBothApiAndServiceAuthorizationChecks() {
        authenticate("ROLE_ADMIN");
        AdminGameController controller = context.getBean(AdminGameController.class);
        GameSessionRepository sessions = context.getBean(GameSessionRepository.class);
        TicketRepository tickets = context.getBean(TicketRepository.class);
        when(sessions.findById(17L)).thenReturn(Optional.empty());
        when(tickets.findById(23L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.session(17L))
                .isInstanceOf(com.bakaroo.bingo.backend.common.exception.NotFoundException.class);
        assertThatThrownBy(() -> controller.ticket(23L))
                .isInstanceOf(com.bakaroo.bingo.backend.common.exception.NotFoundException.class);
    }

    @Test
    void agentMayReadSessionThroughAgentServiceButNotAdminApiOrAdminTicket() {
        authenticate("ROLE_AGENT");
        AdminGameController controller = context.getBean(AdminGameController.class);
        BingoGameService service = context.getBean(BingoGameService.class);
        GameSessionRepository sessions = context.getBean(GameSessionRepository.class);
        TicketRepository tickets = context.getBean(TicketRepository.class);
        when(sessions.findById(17L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.session(17L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAdminTicket(23L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getSession(17L))
                .isInstanceOf(com.bakaroo.bingo.backend.common.exception.NotFoundException.class);

        verify(sessions).findById(17L);
        verify(tickets, never()).findById(23L);
    }

    @Test
    void playerCannotReadAdminGameRecordsAtApiOrServiceBoundaries() {
        authenticate("ROLE_PLAYER");
        AdminGameController controller = context.getBean(AdminGameController.class);
        BingoGameService service = context.getBean(BingoGameService.class);
        GameSessionRepository sessions = context.getBean(GameSessionRepository.class);
        TicketRepository tickets = context.getBean(TicketRepository.class);

        assertThatThrownBy(() -> controller.session(17L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.ticket(23L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getSession(17L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAdminTicket(23L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getAdminPlayerTickets(88L, Pageable.unpaged()))
                .isInstanceOf(AccessDeniedException.class);

        verify(sessions, never()).findById(17L);
        verify(tickets, never()).findById(23L);
        verify(tickets, never()).findByOwnerIdOrderByPurchasedAtDesc(88L, Pageable.unpaged());
    }

    @Test
    void adminMutationServiceBoundaryRejectsAnonymousPlayerAndSupport() {
        BingoGameService service = context.getBean(BingoGameService.class);
        assertThatThrownBy(() -> service.createRoomForAdmin(null, null))
                .isInstanceOf(AccessDeniedException.class);
        for (UserRole role : List.of(UserRole.PLAYER, UserRole.SUPPORT)) {
            var user = principal(role);
            assertThatThrownBy(() -> service.createRoomForAdmin(user, null))
                    .as("%s cannot create game rooms", role).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.updateRoomForAdmin(user, 1L, null))
                    .as("%s cannot edit game rooms", role).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.createSessionForAdmin(user, 1L, null))
                    .as("%s cannot create game sessions", role).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.updateSessionForAdmin(user, 1L, null))
                    .as("%s cannot edit game sessions", role).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.updateSessionStateForAdmin(user, 1L, null))
                    .as("%s cannot change game session state", role).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.triggerAutomaticDraw(1L, user))
                    .as("%s cannot trigger Admin game draws", role).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void adminIdentityPassesAdminMutationServiceBoundary() {
        BingoGameService service = context.getBean(BingoGameService.class);
        // Null request reaches the existing request validation after role authorization.
        assertThatThrownBy(() -> service.createRoomForAdmin(principal(UserRole.ADMIN), null))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void roomMutationApiRoleMatrixIsAnonymousPlayerSupportDeniedAndAdminAllowed() {
        AdminGameController controller = context.getBean(AdminGameController.class);
        assertThatThrownBy(() -> controller.createRoom(null, null))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);

        for (UserRole role : List.of(UserRole.PLAYER, UserRole.SUPPORT)) {
            authenticate("ROLE_" + role.name());
            assertThatThrownBy(() -> controller.createRoom(principal(role), null))
                    .as("%s cannot use Admin room mutations", role)
                    .isInstanceOf(AccessDeniedException.class);
        }

        authenticate("ROLE_ADMIN");
        assertThatThrownBy(() -> controller.createRoom(principal(UserRole.ADMIN), null))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    private static BingoUserPrincipal principal(UserRole role) {
        User user = User.create("+251911000000", "Test", "hash", AccountStatus.ACTIVE, Set.of(role), Instant.now());
        return new BingoUserPrincipal(user);
    }

    private static void authenticate(String authority) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated("test", null,
                List.of(new SimpleGrantedAuthority(authority)));
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        GameSessionRepository sessions() {
            return mock(GameSessionRepository.class);
        }

        @Bean
        TicketRepository tickets() {
            return mock(TicketRepository.class);
        }

        @Bean
        BingoGameService bingoGameService(GameSessionRepository sessions, TicketRepository tickets) {
            return new BingoGameService(null, sessions, tickets, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null);
        }

        @Bean
        AdminGameController adminGameController(BingoGameService gameService) {
            return new AdminGameController(gameService, null, null);
        }
    }
}
