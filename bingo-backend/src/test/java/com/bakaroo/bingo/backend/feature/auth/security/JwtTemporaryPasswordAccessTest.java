package com.bakaroo.bingo.backend.feature.auth.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bakaroo.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.bakaroo.bingo.backend.feature.auth.service.BingoUserPrincipal;
import com.bakaroo.bingo.backend.feature.auth.service.JwtService;
import com.bakaroo.bingo.backend.feature.user.entity.AccountStatus;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import com.bakaroo.bingo.backend.feature.user.entity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTemporaryPasswordAccessTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({
        "GET, /api/v1/auth/me, true",
        "PUT, /api/v1/admin/me/password, true",
        "GET, /api/v1/admin/users, false",
        "PUT, /api/v1/auth/me, false",
        "GET, /api/v1/admin/me/password, false",
        "GET, /api/v1/admin/agents, false"
    })
    void temporaryAdminCanRefreshIdentityAndChangePasswordButCannotUseOtherResources(
            String method, String path, boolean allowed) throws Exception {
        JwtService jwt = mock(JwtService.class);
        BingoUserDetailsService details = mock(BingoUserDetailsService.class);
        User user = User.create(null, "Bootstrap Admin", "hash", AccountStatus.ACTIVE,
                Set.of(UserRole.ADMIN), Instant.now());
        ReflectionTestUtils.setField(user, "id", 42L);
        user.requirePasswordChangeUntil(Instant.now().plusSeconds(3600));
        when(jwt.extractUserId("token")).thenReturn(42L);
        when(jwt.isTokenValidForUser("token", 42L)).thenReturn(true);
        when(details.loadById(42L)).thenReturn(new BingoUserPrincipal(user));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, details, new ObjectMapper().findAndRegisterModules());
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        if (allowed) {
            verify(chain).doFilter(request, response);
            assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                    .stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
        } else {
            verifyNoInteractions(chain);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
        }
    }
}
