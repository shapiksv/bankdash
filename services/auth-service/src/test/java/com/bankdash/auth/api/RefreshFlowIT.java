package com.bankdash.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.testsupport.ContainersConfig;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
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

/** signup → login → refresh → reuse detection → logout, against a real Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class RefreshFlowIT {

    private static final Pattern REFRESH_COOKIE = Pattern.compile("^bd_refresh=([^;]*);");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    final String email = "user-" + UUID.randomUUID() + "@example.com";

    @BeforeEach
    void signup() {
        assertThat(mvc.post().uri("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Jane Doe", "email": "%s", "password": "secret123"}""".formatted(email))
                .exchange()).hasStatus(HttpStatus.CREATED);
    }

    MvcTestResult login() {
        return mvc.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "secret123"}""".formatted(email))
                .exchange();
    }

    MvcTestResult refresh(String refreshToken) {
        return mvc.post().uri("/auth/refresh").cookie(new Cookie("bd_refresh", refreshToken)).exchange();
    }

    static String refreshTokenOf(MvcTestResult response) {
        Matcher matcher = REFRESH_COOKIE.matcher(response.getResponse().getHeader("Set-Cookie"));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    static String accessTokenOf(MvcTestResult response) throws Exception {
        String body = response.getResponse().getContentAsString();
        return body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void refreshIssuesANewAccessTokenAndRotatesTheCookie() throws Exception {
        String first = refreshTokenOf(login());

        var refreshed = refresh(first);

        assertThat(refreshed).hasStatus(HttpStatus.OK);
        assertThat(refreshed).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
        String second = refreshTokenOf(refreshed);
        assertThat(second).isNotEqualTo(first);

        // The rotated token works again, and the new access token is accepted.
        var again = refresh(second);
        assertThat(again).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/auth/me").header("Authorization", "Bearer " + accessTokenOf(again)).exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.email").isEqualTo(email);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeLogin() {
        String stolen = refreshTokenOf(login());
        String legitimate = refreshTokenOf(refresh(stolen));

        var reuse = refresh(stolen);

        assertThat(reuse).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(reuse).hasContentType("application/problem+json");
        assertThat(reuse.getResponse().getHeader("Set-Cookie")).startsWith("bd_refresh=;").contains("Max-Age=0");
        // The token the real user holds is dead too: both sides must log in again.
        assertThat(refresh(legitimate)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anotherLoginIsNotAffectedByReuse() {
        String stolen = refreshTokenOf(login());
        refresh(stolen);
        String otherDevice = refreshTokenOf(login());

        refresh(stolen);

        assertThat(refresh(otherDevice)).hasStatus(HttpStatus.OK);
    }

    @Test
    void logoutRevokesTheLoginAndClearsTheCookie() {
        String token = refreshTokenOf(login());

        var logout = mvc.post().uri("/auth/logout").cookie(new Cookie("bd_refresh", token)).exchange();

        assertThat(logout).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(logout.getResponse().getHeader("Set-Cookie")).startsWith("bd_refresh=;").contains("Max-Age=0");
        assertThat(refresh(token)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutWithoutACookieStillSucceeds() {
        assertThat(mvc.post().uri("/auth/logout").exchange()).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void refreshWithoutACookieIs401() {
        assertThat(mvc.post().uri("/auth/refresh").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anExpiredRefreshTokenIs401() {
        String token = refreshTokenOf(login());
        jdbc.sql("""
                        UPDATE refresh_token SET expires_at = now() - interval '1 second'
                        WHERE user_id = (SELECT id FROM user_credential WHERE email = ?)""")
                .param(email).update();

        assertThat(refresh(token)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onlyTheHashIsStored() {
        String token = refreshTokenOf(login());

        Integer plain = jdbc.sql("SELECT count(*) FROM refresh_token WHERE token_hash = ?").param(token)
                .query(Integer.class).single();
        assertThat(plain).isZero();
    }
}
