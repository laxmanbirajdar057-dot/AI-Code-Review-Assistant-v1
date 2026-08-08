package com.laxman.codereviewassistant.security;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.laxman.codereviewassistant.entity.Role;
import com.laxman.codereviewassistant.entity.User;
import com.laxman.codereviewassistant.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtFilterTest {

    private JwtUtil jwtUtil;
    private UserRepository userRepository;
    private JwtFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    private static final String TOKEN = "a-valid-looking-token";
    private static final String EMAIL = "admin@test.com";

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        userRepository = mock(UserRepository.class);
        filter = new JwtFilter(jwtUtil, userRepository);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtUtil.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtUtil.extractEmail(TOKEN)).thenReturn(EMAIL);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenForKnownUserAttachesRolePrefixedAuthority() throws Exception {
        User admin = new User();
        admin.setEmail(EMAIL);
        admin.setRole(Role.ADMIN);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin));

        filter.doFilter(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validTokenForDeletedUserLeavesRequestUnauthenticated() throws Exception {
        // Token is still cryptographically valid, but the user it points to no
        // longer exists (e.g. deleted after the token was issued). This must
        // not throw — it should just fall through unauthenticated so the
        // request gets a clean 401 from the entry point instead of a 500.
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}
