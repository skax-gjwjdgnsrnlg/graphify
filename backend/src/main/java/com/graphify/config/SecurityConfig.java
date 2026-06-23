package com.graphify.config;

import com.graphify.auth.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiSecurityJsonHandlers apiSecurityJsonHandlers;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiSecurityJsonHandlers apiSecurityJsonHandlers
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiSecurityJsonHandlers = apiSecurityJsonHandlers;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                                .preload(true)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/api/v1/bootstrap/**",
                                "/api/v1/search/**",
                                "/api/v1/home/**",
                                "/api/v1/companies/search",
                                "/api/v1/companies/resolve",
                                "/api/v1/companies/*/sync",
                                "/api/v1/companies/*",
                                "/api/v1/companies/*/insights",
                                "/api/v1/companies/*/insights/generate",
                                "/api/v1/companies/*/market-technical",
                                "/api/v1/companies/*/graph",
                                "/api/v1/agent/stream/**",
                                "/api/v1/terms/**",
                                "/api/v1/auth/login",
                                "/api/v1/auth/oauth/**",
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/validate",
                                "/api/v1/auth/password-reset/confirm",
                                "/api/npo/**",
                                "/internal/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiSecurityJsonHandlers)
                        .accessDeniedHandler(apiSecurityJsonHandlers))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
