package com.bankdash.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.testsupport.ContainersConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class SignupLoginIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    final String email = "user-" + UUID.randomUUID() + "@example.com";

    MvcTestResult signup(String email, String password) {
        return mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Jane Doe", "email": "%s", "password": "%s"}""".formatted(email, password))
                .exchange();
    }

    MvcTestResult login(String email, String password) {
        return mvc.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password))
                .exchange();
    }

    @Test
    void signupCreatesTheUserAndLogsThemIn() {
        var response = signup(email, "secret123");

        assertThat(response).hasStatus(HttpStatus.CREATED);
        assertThat(response).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
        assertThat(response).bodyJson().extractingPath("$.expiresIn").isEqualTo(900);
        assertThat(response).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
        assertThat(response).containsHeader("Cache-Control");
        assertRefreshCookie(response);

        String hash = jdbc.sql("SELECT password_hash FROM user_credential WHERE email = ?").param(email)
                .query(String.class).single();
        assertThat(hash).startsWith("$2a$").isNotEqualTo("secret123");
    }

    @Test
    void duplicateEmailIs409EvenWithDifferentCase() {
        signup(email, "secret123");

        var response = signup(email.toUpperCase(), "secret456");

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
        assertThat(response).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Email already registered");
    }

    @Test
    void invalidSignupIs400WithFieldErrors() {
        var response = mvc.post().uri("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "", "email": "not-an-email", "password": "secret123"}""")
                .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("fullName", "email");
    }

    @Test
    void shortPasswordIs400() {
        var response = signup(email, "short");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("password");
    }

    @Test
    void loginWithTheRightPasswordIssuesTokens() {
        signup(email, "secret123");

        var response = login("  " + email.toUpperCase(), "secret123");

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
        assertRefreshCookie(response);
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() {
        signup(email, "secret123");

        var wrongPassword = login(email, "secret999");
        var unknownEmail = login("nobody-" + email, "secret123");

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getResponse().getContentAsByteArray())
                .isEqualTo(unknownEmail.getResponse().getContentAsByteArray());
        assertThat(wrongPassword).doesNotContainHeader("Set-Cookie");
    }

    private static void assertRefreshCookie(MvcTestResult response) {
        String cookie = response.getResponse().getHeader("Set-Cookie");
        assertThat(cookie)
                .startsWith("bd_refresh=")
                .contains("Path=/api/auth", "Max-Age=604800", "HttpOnly", "Secure", "SameSite=Strict");
    }
}
