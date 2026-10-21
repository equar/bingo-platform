package com.equar.bingo.backend.config;

import com.equar.bingo.backend.common.api.ApiErrorResponseWriter;
import com.equar.bingo.backend.feature.auth.security.JwtAuthenticationFilter;
import com.equar.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        private final List<String> allowedOrigins;

        public SecurityConfig(@Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3001,http://127.0.0.1:3001}") String allowedOrigins) {
                this.allowedOrigins = parseOrigins(allowedOrigins);
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(allowedOrigins);
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
                configuration.setExposedHeaders(List.of("X-Correlation-Id"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public DaoAuthenticationProvider daoAuthenticationProvider(BingoUserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder);
                return provider;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                        JwtAuthenticationFilter jwtAuthenticationFilter,
                        ObjectMapper objectMapper,
                        DaoAuthenticationProvider daoAuthenticationProvider) throws Exception {
                ApiErrorResponseWriter errorWriter = new ApiErrorResponseWriter(objectMapper);

                http.csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())
                                .authenticationProvider(daoAuthenticationProvider)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers("/api/v1/auth/**", "/api/v1/admin/login",
                                                                "/api/v1/agent/login", "/v3/api-docs/**",
                                                                "/swagger-ui/**", "/swagger-ui.html",
                                                                "/actuator/health", "/ws", "/")
                                                .permitAll()
                                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                                .requestMatchers("/api/v1/admin/support/**")
                                                .hasAnyRole("ADMIN", "SUPPORT")
                                                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/api/v1/agent/**").hasRole("AGENT")
                                                .anyRequest().authenticated())
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(
                                                                (request, response, authException) -> errorWriter.write(
                                                                                response,
                                                                                HttpStatus.UNAUTHORIZED.value(),
                                                                                "UNAUTHORIZED",
                                                                                "Authentication is required",
                                                                                request.getRequestURI(),
                                                                                ApiErrorResponseWriter
                                                                                                .resolveCorrelationId(
                                                                                                                request)))
                                                .accessDeniedHandler((request, response,
                                                                accessDeniedException) -> errorWriter.write(
                                                                                response,
                                                                                HttpStatus.FORBIDDEN.value(),
                                                                                "FORBIDDEN",
                                                                                "You do not have permission to access this resource",
                                                                                request.getRequestURI(),
                                                                                ApiErrorResponseWriter
                                                                                                .resolveCorrelationId(
                                                                                                                request))))
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        private static List<String> parseOrigins(String rawOrigins) {
                return Arrays.stream(rawOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .distinct()
                                .collect(Collectors.toList());
        }

        @Bean
        public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
                return new ProviderManager(daoAuthenticationProvider);
        }
}