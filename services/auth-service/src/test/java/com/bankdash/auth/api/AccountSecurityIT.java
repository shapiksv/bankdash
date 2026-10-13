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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Settings → Security: password change and the two-factor flag. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class AccountSecurityIT {

    private static final Pattern REFRESH_COOKIE = Pattern.compile("^bd_refresh=([^;]*);");

    @Autowired
    MockMvcTester mvc;

    final String email = "user-" + UUID.randomUUID() + "@example.com";
    MvcTestResult signup;

    @BeforeEach
    void signup() {
        signup = mvc.post().uri("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Jane Doe", "email": "%s", "password": "secret123"}""".formatted(email))
                .exchange();
        assertThat(signup).hasStatus(HttpStatus.CREATED);
    }

    static String accessTokenOf(MvcTestResult response) throws Exception {
        return response.getResponse().getContentAsString().replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    static String refreshTokenOf(MvcTestResult response) {
        Matcher matcher = REFRESH_COOKIE.matcher(response.getResponse().getHeader("Set-Cookie"));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    MvcTestResult login(String password) {
        return mvc.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password))
                .exchange();
    }

    MvcTestResult changePassword(String accessToken, String current, String next) {
        return mvc.put().uri("/auth/password")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}""".formatted(current, next))
                .exchange();
    }

    MvcTestResult refresh(String refreshToken) {
        return mvc.post().uri("/auth/refresh").cookie(new Cookie("bd_refresh", refreshToken)).exchange();
    }

    @Test
    void changingThePasswordLogsOutOtherSessionsAndKeepsThisOne() throws Exception {
        String otherDevice = refreshTokenOf(login("secret123"));

        var changed = changePassword(accessTokenOf(signup), "secret123", "new-secret-456");

        assertThat(changed).hasStatus(HttpStatus.OK);
        assertThat(changed).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
        String thisDevice = refreshTokenOf(changed);
        assertThat(refresh(otherDevice)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(refreshTokenOf(signup))).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(thisDevice)).hasStatus(HttpStatus.OK);
        assertThat(login("secret123")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login("new-secret-456")).hasStatus(HttpStatus.OK);
    }

    @Test
    void aWrongCurrentPasswordIsAFieldError() throws Exception {
        var response = changePassword(accessTokenOf(signup), "not-my-password", "new-secret-456");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("currentPassword");
        assertThat(login("secret123")).hasStatus(HttpStatus.OK);
    }

    @Test
    void aWeakNewPasswordIsAFieldError() throws Exception {
        var response = changePassword(accessTokenOf(signup), "secret123", "short");

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("newPassword");
    }

    @Test
    void missingFieldsAreFieldErrors() throws Exception {
        var response = mvc.put().uri("/auth/password")
                .header("Authorization", "Bearer " + accessTokenOf(signup))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("currentPassword", "newPassword");
    }

    @Test
    void changingThePasswordNeedsAnAccessToken() {
        var response = mvc.put().uri("/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "secret123", "newPassword": "new-secret-456"}""")
                .exchange();

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType("application/problem+json");
    }

    @Test
    void twoFactorIsStoredAndShownByMe() throws Exception {
        String token = accessTokenOf(signup);
        assertThat(mvc.get().uri("/auth/me").header("Authorization", "Bearer " + token).exchange())
                .bodyJson().extractingPath("$.twoFactorEnabled").isEqualTo(false);

        var response = mvc.put().uri("/auth/two-factor")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"enabled": true}""")
                .exchange();

        assertThat(response).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"twoFactorEnabled": true}""");
        assertThat(mvc.get().uri("/auth/me").header("Authorization", "Bearer " + token).exchange())
                .bodyJson().extractingPath("$.twoFactorEnabled").isEqualTo(true);
    }

    @Test
    void twoFactorNeedsTheFlag() throws Exception {
        var response = mvc.put().uri("/auth/two-factor")
                .header("Authorization", "Bearer " + accessTokenOf(signup))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .exchange();

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("enabled");
    }
}
