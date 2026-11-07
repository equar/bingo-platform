package com.bakaroo.bingo.backend.feature.auth.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.bakaroo.bingo.backend.feature.auth.service.*;
import com.bakaroo.bingo.backend.feature.user.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class JwtSuspensionTest {
    @Test
    void validTokenReceivesSuspensionButInvalidTokenDoesNotDiscloseStatus() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(BingoUserDetailsService.class);
        var player = User.create("251911000004", "Player", "hash", AccountStatus.SUSPENDED,
                Set.of(UserRole.PLAYER), Instant.now());
        ReflectionTestUtils.setField(player, "id", 4L);
        when(jwt.extractUserId("token")).thenReturn(4L);
        when(users.loadById(4L)).thenReturn(new BingoUserPrincipal(player));
        when(jwt.isTokenValidForUser("token", 4L)).thenReturn(true);
        var filter = new JwtAuthenticationFilter(jwt, users, new ObjectMapper().findAndRegisterModules());
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_SUSPENDED"));
        assertTrue(response.getContentAsString().contains("0956047345"));
        verifyNoInteractions(chain);

        when(jwt.isTokenValidForUser("token", 4L)).thenReturn(false);
        var invalidResponse = new MockHttpServletResponse();
        filter.doFilter(request, invalidResponse, chain);
        assertFalse(invalidResponse.getContentAsString().contains("ACCOUNT_SUSPENDED"));
        verify(chain).doFilter(request, invalidResponse);
    }
}
