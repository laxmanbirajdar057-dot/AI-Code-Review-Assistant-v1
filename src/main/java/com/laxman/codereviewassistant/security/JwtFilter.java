package com.laxman.codereviewassistant.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.laxman.codereviewassistant.entity.User;
import com.laxman.codereviewassistant.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtil.isTokenValid(token)) {
                String email = jwtUtil.extractEmail(token);

                // Fix: authorities were previously always null, so hasRole(...)
                // checks in SecurityConfig had nothing to evaluate against. We look
                // the user up by the email in the token and attach a single
                // ROLE_<role> authority (the "ROLE_" prefix is required by Spring
                // Security's hasRole() convention — it's stripped internally).
                //
                // If the token is valid but the user no longer exists (e.g. deleted
                // after the token was issued), we deliberately leave the request
                // unauthenticated rather than throwing — a thrown exception here
                // would turn into a raw 500 for every request bearing a stale
                // token, instead of a clean 401 from the entry point.
                Optional<User> user = userRepository.findByEmail(email);

                if (user.isPresent()) {
                    List<GrantedAuthority> authorities =
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name()));

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(email, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}