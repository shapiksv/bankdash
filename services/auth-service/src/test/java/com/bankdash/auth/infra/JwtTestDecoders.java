package com.bankdash.auth.infra;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** The production access-token decoder, exposed to tests in other packages. */
public final class JwtTestDecoders {

    private JwtTestDecoders() {}

    public static JwtDecoder forKey(RSAKey key) {
        try {
            return JwtConfig.accessTokenDecoder(key, "bankdash-auth", "bankdash-api");
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
