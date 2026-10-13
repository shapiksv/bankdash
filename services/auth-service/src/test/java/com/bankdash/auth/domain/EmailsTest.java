package com.bankdash.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailsTest {

    @Test
    void trimsAndLowercases() {
        assertThat(Emails.normalize("  Jane.Doe@Example.COM ")).isEqualTo("jane.doe@example.com");
    }
}
