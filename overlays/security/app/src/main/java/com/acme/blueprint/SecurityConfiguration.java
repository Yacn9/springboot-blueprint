package com.acme.blueprint;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The API is an OAuth2 resource server: every request needs a JWT signed by
 * the keys at {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri},
 * except health, info and the OpenAPI documents. It lives in the composition
 * root, so modules stay free of security code and their tests of it; a module
 * that needs the caller asks Spring Security's context in its web layer.
 *
 * <p>Refusals are problem details with a stable code, like every other error.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    private static final String[] PUBLIC = {
            "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Bearer tokens, no session cookie: there is nothing for CSRF to ride on.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers(PUBLIC).permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, refusal) ->
                                problem(response, HttpStatus.UNAUTHORIZED, "request.unauthenticated")))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, refusal) ->
                                problem(response, HttpStatus.UNAUTHORIZED, "request.unauthenticated"))
                        .accessDeniedHandler((request, response, refusal) ->
                                problem(response, HttpStatus.FORBIDDEN, "request.forbidden")))
                .build();
    }

    private static void problem(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"code\":\"%s\"}"
                .formatted(status.getReasonPhrase(), status.value(), code));
    }
}
