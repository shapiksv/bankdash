package com.bankdash.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.testsupport.TestJwtIssuer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** A minimal service using web-common, with real RS256 tokens from {@link TestJwtIssuer}. */
@SpringBootTest(classes = WebCommonIT.TestService.class)
@AutoConfigureMockMvc
class WebCommonIT {

    static final TestJwtIssuer issuer = TestJwtIssuer.start();

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", issuer::jwksUri);
    }

    @AfterAll
    static void stop() {
        issuer.close();
    }

    @SpringBootApplication
    static class TestService {

        @RestController
        static class TestController {

            @GetMapping("/whoami")
            String whoami(@CurrentUser UUID userId) {
                return userId.toString();
            }

            record Body(@NotBlank String name, @Size(max = 3) String code) {}

            @PutMapping("/things")
            Body put(@Valid @RequestBody Body body) {
                return body;
            }
        }
    }

    @Autowired
    MockMvcTester mvc;

    final UUID user = UUID.randomUUID();

    @Test
    void currentUserIsTheTokenSubject() {
        var response = mvc.get().uri("/whoami").header("Authorization", "Bearer " + issuer.token(user)).exchange();

        assertThat(response).hasStatusOk().hasBodyTextEqualTo(user.toString());
    }

    @Test
    void missingTokenIsA401ProblemWithTheBearerChallenge() {
        var response = mvc.get().uri("/whoami").exchange();

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType("application/problem+json");
        assertThat(response).headers().hasHeaderSatisfying("WWW-Authenticate", values -> assertThat(values.getFirst()).startsWith("Bearer"));
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Unauthorized");
    }

    @Test
    void wrongAudienceIs401() {
        var token = issuer.token(user, claims -> claims.audience("someone-else"));

        assertThat(mvc.get().uri("/whoami").header("Authorization", "Bearer " + token).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongIssuerIs401() {
        var token = issuer.token(user, claims -> claims.issuer("evil"));

        assertThat(mvc.get().uri("/whoami").header("Authorization", "Bearer " + token).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validationErrorsAreA400ProblemWithFieldErrors() {
        var response = mvc.put().uri("/things")
                .header("Authorization", "Bearer " + issuer.token(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": " ", "code": "toolong"}""")
                .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Invalid request");
        assertThat(response).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("code", "name");
    }

    @Test
    void malformedJsonIsA400Problem() {
        var response = mvc.put().uri("/things")
                .header("Authorization", "Bearer " + issuer.token(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json")
                .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
    }

    @Test
    void healthNeedsNoToken() {
        // No actuator here, so 404 rather than 401 proves the path is not behind authentication.
        assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }
}
