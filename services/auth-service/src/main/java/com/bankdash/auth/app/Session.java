package com.bankdash.auth.app;

import com.bankdash.auth.app.AccessTokenIssuer.AccessToken;
import java.time.Instant;

/** What a successful signup, login or refresh hands to the client. */
public record Session(AccessToken accessToken, String refreshToken, Instant refreshTokenExpiresAt) {}
