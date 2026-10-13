package com.bankdash.auth.app;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Token and cookie settings ({@code bankdash.auth.*}). The defaults are the values from
 * {@code docs/adr/0001-jwt-and-refresh-tokens.md}.
 *
 * @param issuer         {@code iss} of every access token
 * @param audience       {@code aud} of every access token
 * @param accessTokenTtl lifetime of an access token
 * @param refreshTokenTtl sliding lifetime of a refresh token, counted from its last use
 * @param refreshFamilyTtl absolute lifetime of a login (all tokens rotated from it)
 * @param privateKey     PKCS#8 PEM with the RS256 signing key; when absent an ephemeral key is generated
 * @param bcryptStrength BCrypt cost factor
 * @param refreshCookie  the httpOnly cookie that carries the refresh token
 */
@ConfigurationProperties("bankdash.auth")
public record AuthProperties(
        @DefaultValue("bankdash-auth") String issuer,
        @DefaultValue("bankdash-api") String audience,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        @DefaultValue("30d") Duration refreshFamilyTtl,
        @Nullable Resource privateKey,
        @DefaultValue("12") int bcryptStrength,
        @DefaultValue RefreshCookie refreshCookie) {

    /**
     * @param name   cookie name
     * @param path   path as the browser sees it, i.e. behind the gateway's {@code /api} prefix
     * @param secure whether to set the {@code Secure} attribute; off only in the dev profile
     */
    public record RefreshCookie(
            @DefaultValue("bd_refresh") String name,
            @DefaultValue("/api/auth") String path,
            @DefaultValue("true") boolean secure) {}
}
