package com.bankdash.profile.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.profile.ProfileIT;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class AvatarApiIT extends ProfileIT {

    /** A real 1x1 PNG. */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYPj/HwADAgH/eL9GtQAAAABJRU5ErkJggg==");

    MvcTestResult upload(UUID userId, String contentType, byte[] body) {
        return mvc.put().uri("/profile/me/avatar")
                .header("Authorization", bearer(userId))
                .contentType(contentType)
                .content(body)
                .exchange();
    }

    MvcTestResult download(UUID userId) {
        return mvc.get().uri("/profile/me/avatar").header("Authorization", bearer(userId)).exchange();
    }

    @Test
    void uploadsAndDownloadsTheAvatar() {
        UUID userId = registeredUser("Jane Doe");

        assertThat(upload(userId, "image/png", PNG)).hasStatus(HttpStatus.NO_CONTENT);

        var response = download(userId);
        assertThat(response).hasStatusOk().hasContentType("image/png");
        assertThat(response).headers().hasValue("X-Content-Type-Options", "nosniff");
        assertThat(response.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(mvc.get().uri("/profile/me").header("Authorization", bearer(userId)).exchange())
                .bodyJson().extractingPath("$.avatarUpdatedAt").asString().isNotBlank();
    }

    @Test
    void contentTypeParametersAreIgnoredAsTheGatewayAddsACharset() {
        UUID userId = registeredUser("Jane Doe");

        assertThat(upload(userId, "image/png;charset=UTF-8", PNG)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(download(userId)).hasContentType("image/png");
    }

    @Test
    void aNewUploadReplacesTheOldOne() {
        UUID userId = registeredUser("Jane Doe");
        upload(userId, "image/png", PNG);
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

        upload(userId, "image/jpeg", jpeg);

        var response = download(userId);
        assertThat(response).hasContentType("image/jpeg");
        assertThat(response.getResponse().getContentAsByteArray()).isEqualTo(jpeg);
    }

    @Test
    void noAvatarIsA404AndTheProfileSaysSo() {
        UUID userId = registeredUser("Jane Doe");

        assertThat(download(userId)).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
        assertThat(mvc.get().uri("/profile/me").header("Authorization", bearer(userId)).exchange())
                .bodyJson().extractingPath("$.avatarUpdatedAt").isNull();
    }

    @Test
    void moreThanOneMegabyteIs413() {
        UUID userId = registeredUser("Jane Doe");

        var response = upload(userId, "image/png", Arrays.copyOf(PNG, 1024 * 1024 + 1));

        assertThat(response).hasStatus(HttpStatus.CONTENT_TOO_LARGE).hasContentType("application/problem+json");
        assertThat(download(userId)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherContentTypeIs415() {
        UUID userId = registeredUser("Jane Doe");

        assertThat(upload(userId, "text/html", "<script>".getBytes()))
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .hasContentType("application/problem+json");
    }

    @Test
    void bytesThatAreNotTheDeclaredTypeAre400() {
        UUID userId = registeredUser("Jane Doe");

        var response = upload(userId, "image/png", "<svg onload=alert(1)>".getBytes());

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.detail").isEqualTo("The file is not a valid PNG image");
    }

    @Test
    void noProfileYetIs404() {
        assertThat(upload(UUID.randomUUID(), "image/png", PNG)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
