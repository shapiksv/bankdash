package com.bankdash.auth.api;

import com.bankdash.auth.app.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh-token cookie: httpOnly, SameSite=Strict, and scoped to {@code /api/auth} so the browser only
 * sends it to refresh and logout.
 */
@Component
public class RefreshCookies {

    private final AuthProperties.RefreshCookie settings;
    private final Clock clock;

    public RefreshCookies(AuthProperties properties, Clock clock) {
        this.settings = properties.refreshCookie();
        this.clock = clock;
    }

    public String name() {
        return settings.name();
    }

    public ResponseCookie issue(String refreshToken, Instant expiresAt) {
        // Round up: the token expiry was computed a few milliseconds before this cookie.
        long millis = Duration.between(clock.instant(), expiresAt).toMillis();
        return builder(refreshToken).maxAge(Math.max(0, (millis + 999) / 1000)).build();
    }

    public ResponseCookie clear() {
        return builder("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(settings.name(), value)
                .httpOnly(true)
                .secure(settings.secure())
                .sameSite("Strict")
                .path(settings.path());
    }
}
