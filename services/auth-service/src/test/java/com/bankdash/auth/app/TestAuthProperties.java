package com.bankdash.auth.app;

import java.time.Duration;

/** {@link AuthProperties} with the production defaults, for unit tests. */
public final class TestAuthProperties {

    private TestAuthProperties() {}

    public static AuthProperties defaults() {
        return new AuthProperties(
                "bankdash-auth",
                "bankdash-api",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                Duration.ofDays(30),
                null,
                4,
                new AuthProperties.RefreshCookie("bd_refresh", "/api/auth", true));
    }
}
