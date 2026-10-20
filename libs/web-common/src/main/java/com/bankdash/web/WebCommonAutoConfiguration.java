package com.bankdash.web;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes a service a stateless OAuth2 resource server. Tokens are validated by Boot's JwtDecoder, configured per
 * service with {@code spring.security.oauth2.resourceserver.jwt.*} (auth-service's JWKS, {@code iss}, {@code aud}).
 * Services never add CORS headers: CORS is the gateway's job.
 */
@AutoConfiguration(beforeName = {
    "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
    "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
    "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration",
}, afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
public class WebCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint(JsonMapper jsonMapper) {
        return new ProblemDetailAuthenticationEntryPoint(jsonMapper);
    }

    /** Every endpoint needs an access token, except health checks. */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http, ProblemDetailAuthenticationEntryPoint entryPoint) {
        return http
                // Stateless and authenticated by the Authorization header only, so there is nothing for CSRF to forge.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailsExceptionHandler problemDetailsExceptionHandler() {
        return new ProblemDetailsExceptionHandler();
    }

    @Bean
    WebMvcConfigurer currentUserWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }
}
