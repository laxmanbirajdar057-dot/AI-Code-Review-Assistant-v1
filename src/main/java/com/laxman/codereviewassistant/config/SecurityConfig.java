package com.laxman.codereviewassistant.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.laxman.codereviewassistant.security.JwtAuthenticationEntryPoint;
import com.laxman.codereviewassistant.security.JwtFilter;

@Configuration
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(JwtFilter jwtFilter, JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtFilter = jwtFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*")); // fine for local dev; restrict later
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Fix: Role (ADMIN/DEVELOPER/VIEWER) was stored on User but never
                // actually enforced anywhere — every authenticated user had identical
                // access. Rules below match the permission matrix:
                //   ADMIN      — full access everywhere (ownership bypass is handled
                //                 in the service layer, see RepositoryService /
                //                 ReviewService)
                //   DEVELOPER  — can register/delete repos and resolve comments, but
                //                 only for repos/reviews they own (checked in services)
                //   VIEWER     — read-only: can list/view but not mutate; can still
                //                 use the playground since it touches no stored data
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/login", "/register", "/repos-page", "/review-page", "/playground-page",
                                "/css/**", "/js/**", "/favicon.ico"
                        )
                        .permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/webhooks/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/repos").hasAnyRole("ADMIN", "DEVELOPER")
                        .requestMatchers(HttpMethod.DELETE, "/repos/**").hasAnyRole("ADMIN", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/repos/**").hasAnyRole("ADMIN", "DEVELOPER", "VIEWER")

                        .requestMatchers(HttpMethod.PATCH, "/reviews/comments/**").hasAnyRole("ADMIN", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/reviews/**").hasAnyRole("ADMIN", "DEVELOPER", "VIEWER")

                        .requestMatchers("/snippets/**").hasAnyRole("ADMIN", "DEVELOPER", "VIEWER")

                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}