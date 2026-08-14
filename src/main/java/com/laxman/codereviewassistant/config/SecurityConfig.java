package com.laxman.codereviewassistant.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            @Value("${app.allowed-origins}") String allowedOrigins) {
        this.jwtFilter = jwtFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.allowedOrigins = Arrays.asList(allowedOrigins.split(","));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Fix: was setAllowedOriginPatterns(List.of("*")) combined with
        // allowCredentials(true) — browsers reject a wildcard origin alongside
        // credentialed requests, and Spring itself rejects "*" here once you're
        // not using patterns loosely. Origins now come from app.allowed-origins
        // (APP_ALLOWED_ORIGINS env var, comma-separated), defaulting to local
        // dev ports only.
        config.setAllowedOrigins(allowedOrigins);
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
                //                 ReviewService / ReviewAnalyticsService)
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
                        .requestMatchers("/actuator/health").permitAll()

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