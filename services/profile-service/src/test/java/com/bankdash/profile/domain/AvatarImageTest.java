package com.bankdash.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.bankdash.profile.domain.AvatarImage.AvatarTooLargeException;
import com.bankdash.profile.domain.AvatarImage.InvalidAvatarException;
import com.bankdash.profile.domain.AvatarImage.UnsupportedAvatarTypeException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AvatarImageTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    static final byte[] WEBP = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

    @Test
    void acceptsPngJpegAndWebpWithMatchingBytes() {
        assertThat(AvatarImage.of("image/png", PNG).contentType()).isEqualTo("image/png");
        assertThat(AvatarImage.of("image/jpeg", JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(AvatarImage.of("image/webp", WEBP).contentType()).isEqualTo("image/webp");
    }

    @Test
    void refusesOtherTypes() {
        assertThatExceptionOfType(UnsupportedAvatarTypeException.class).isThrownBy(() -> AvatarImage.of("image/svg+xml", PNG));
        assertThatExceptionOfType(UnsupportedAvatarTypeException.class).isThrownBy(() -> AvatarImage.of("text/html", PNG));
    }

    @Test
    void refusesBytesThatDoNotMatchTheDeclaredType() {
        assertThatExceptionOfType(InvalidAvatarException.class).isThrownBy(() -> AvatarImage.of("image/png", JPEG));
        assertThatExceptionOfType(InvalidAvatarException.class)
                .isThrownBy(() -> AvatarImage.of("image/jpeg", "<html>".getBytes(StandardCharsets.UTF_8)))
                .withMessage("The file is not a valid JPEG image");
        assertThatExceptionOfType(InvalidAvatarException.class).isThrownBy(() -> AvatarImage.of("image/webp", "RIFF".getBytes()));
    }

    @Test
    void refusesEmptyFiles() {
        assertThatExceptionOfType(InvalidAvatarException.class).isThrownBy(() -> AvatarImage.of("image/png", new byte[0]));
    }

    @Test
    void acceptsExactlyOneMegabyteButNotMore() {
        byte[] max = Arrays.copyOf(PNG, AvatarImage.MAX_BYTES);
        byte[] tooBig = Arrays.copyOf(PNG, AvatarImage.MAX_BYTES + 1);

        assertThat(AvatarImage.of("image/png", max).data()).hasSize(1024 * 1024);
        assertThatExceptionOfType(AvatarTooLargeException.class).isThrownBy(() -> AvatarImage.of("image/png", tooBig));
    }
}
