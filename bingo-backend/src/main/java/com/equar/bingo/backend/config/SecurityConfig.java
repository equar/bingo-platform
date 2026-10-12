package com.equar.bingo.backend.config;

import com.equar.bingo.backend.common.api.ApiErrorResponseWriter;
import com.equar.bingo.backend.feature.auth.security.JwtAuthenticationFilter;
import com.equar.bingo.backend.feature.auth.service.BingoUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(BingoUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
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
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/health", "/ws")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> errorWriter.write(
                                response,
                                HttpStatus.UNAUTHORIZED.value(),
                                "UNAUTHORIZED",
                                "Authentication is required",
                                request.getRequestURI(),
                                ApiErrorResponseWriter.resolveCorrelationId(request)))
                        .accessDeniedHandler((request, response, accessDeniedException) -> errorWriter.write(
                                response,
                                HttpStatus.FORBIDDEN.value(),
                                "FORBIDDEN",
                                "You do not have permission to access this resource",
                                request.getRequestURI(),
                                ApiErrorResponseWriter.resolveCorrelationId(request))))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
        return new ProviderManager(daoAuthenticationProvider);
    }
}