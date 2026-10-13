package com.bankdash.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TestJwtIssuerTest {

    final TestJwtIssuer issuer = TestJwtIssuer.start();
    final UUID subject = UUID.randomUUID();

    @AfterEach
    void stop() {
        issuer.close();
    }

    RSAKey publishedKey() throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            String body = http.send(HttpRequest.newBuilder(URI.create(issuer.jwksUri())).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            JWKSet set = JWKSet.parse(body);
            assertThat(set.getKeys()).singleElement().matches(key -> !key.isPrivate());
            return set.getKeys().getFirst().toRSAKey();
        }
    }

    @Test
    void tokensVerifyAgainstThePublishedJwks() throws Exception {
        SignedJWT jwt = SignedJWT.parse(issuer.token(subject));

        RSAKey key = publishedKey();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(key.getKeyID());
        assertThat(jwt.verify(new RSASSAVerifier(key))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(subject.toString());
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("bankdash-auth");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("bankdash-api");
        assertThat(issuer.jwksRequests()).isEqualTo(1);
    }

    @Test
    void expiredTokensAreInThePast() throws Exception {
        var claims = SignedJWT.parse(issuer.expiredToken(subject)).getJWTClaimsSet();

        assertThat(claims.getExpirationTime()).isBefore(new Date());
    }

    @Test
    void unknownKeyTokensDoNotVerify() throws Exception {
        SignedJWT jwt = SignedJWT.parse(issuer.tokenSignedByUnknownKey(subject));

        assertThat(jwt.verify(new RSASSAVerifier(publishedKey()))).isFalse();
    }

    @Test
    void rotateKeyPublishesANewKid() throws Exception {
        String before = publishedKey().getKeyID();

        issuer.rotateKey();

        assertThat(publishedKey().getKeyID()).isNotEqualTo(before);
        assertThat(SignedJWT.parse(issuer.token(subject)).getHeader().getKeyID()).isEqualTo(publishedKey().getKeyID());
    }
}
