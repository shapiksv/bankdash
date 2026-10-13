package com.bankdash.auth.infra;

import static org.springframework.security.config.Customizer.withDefaults;

import com.bankdash.auth.api.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailAuthenticationEntryPoint entryPoint) {
        return http
                // Stateless API: no session, and no cookie is used for authentication except the refresh
                // cookie, which is SameSite=Strict (see ADR 0002).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .build();
    }
}
