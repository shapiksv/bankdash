package com.bankdash.profile.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.profile.ProfileIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class ProfileApiIT extends ProfileIT {

    MvcTestResult put(UUID userId, String body) {
        return mvc.put().uri("/profile/me")
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    static String body(String username) {
        return """
                {"fullName": " Charlene Reed ", "username": "%s", "dateOfBirth": "1990-01-25",
                 "presentAddress": "San Jose, California, USA", "permanentAddress": "  ",
                 "city": "San Jose", "postalCode": "45962", "country": "USA"}""".formatted(username);
    }

    @Test
    void theConsumerCreatedProfileIsReadable() {
        UUID userId = registeredUser("Jane Doe");

        var response = mvc.get().uri("/profile/me").header("Authorization", bearer(userId)).exchange();

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.userId").isEqualTo(userId.toString());
        assertThat(response).bodyJson().extractingPath("$.fullName").isEqualTo("Jane Doe");
        assertThat(response).bodyJson().extractingPath("$.username").asString().startsWith("user-");
        assertThat(response).bodyJson().extractingPath("$.city").isNull();
    }

    @Test
    void savesTheProfileWithNormalizedValues() {
        UUID userId = registeredUser("Jane Doe");
        String username = "Charlene." + userId.toString().substring(0, 8);

        var response = put(userId, body(username));

        assertThat(response).hasStatusOk();
        assertThat(response).bodyJson().extractingPath("$.fullName").isEqualTo("Charlene Reed");
        assertThat(response).bodyJson().extractingPath("$.username").isEqualTo(username.toLowerCase());
        assertThat(response).bodyJson().extractingPath("$.permanentAddress").isNull();
        var reread = mvc.get().uri("/profile/me").header("Authorization", bearer(userId)).exchange();
        assertThat(reread).bodyJson().extractingPath("$.dateOfBirth").isEqualTo("1990-01-25");
        assertThat(reread).bodyJson().extractingPath("$.postalCode").isEqualTo("45962");
    }

    @Test
    void invalidFieldsAreA400ProblemListingEachField() {
        UUID userId = registeredUser("Jane Doe");

        var response = put(userId, """
                {"fullName": "", "username": "no spaces allowed", "dateOfBirth": "2999-01-01",
                 "postalCode": "%s"}""".formatted("9".repeat(21)));

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactly("dateOfBirth", "fullName", "postalCode", "username");
    }

    @Test
    void aTakenUsernameIsA409WithAFieldError() {
        UUID first = registeredUser("First");
        UUID second = registeredUser("Second");
        String username = "taken" + first.toString().substring(0, 8);
        assertThat(put(first, body(username))).hasStatusOk();

        var response = put(second, body(username.toUpperCase()));

        assertThat(response).hasStatus(HttpStatus.CONFLICT).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.errors[0].field").isEqualTo("username");
    }

    @Test
    void keepingYourOwnUsernameIsFine() {
        UUID userId = registeredUser("Jane Doe");
        String username = "mine" + userId.toString().substring(0, 8);
        put(userId, body(username));

        assertThat(put(userId, body(username))).hasStatusOk();
    }

    @Test
    void noProfileYetIsA404Problem() {
        var response = mvc.get().uri("/profile/me").header("Authorization", bearer(UUID.randomUUID())).exchange();

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.title").isEqualTo("Profile not found");
    }

    @Test
    void noTokenIs401() {
        assertThat(mvc.get().uri("/profile/me").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType("application/problem+json");
    }

    @Test
    void healthIsPublic() {
        assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatusOk();
    }
}
