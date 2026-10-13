package com.bankdash.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A stand-in for auth-service in tests of services that only <em>validate</em> tokens (the gateway and,
 * later, every resource server). It serves a JWKS on a random local port and mints RS256 tokens with the
 * same claims auth-service issues.
 *
 * <pre>{@code
 * static final TestJwtIssuer issuer = TestJwtIssuer.start();
 *
 * @DynamicPropertySource
 * static void jwt(DynamicPropertyRegistry registry) {
 *     registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", issuer::jwksUri);
 * }
 * }</pre>
 */
public final class TestJwtIssuer implements AutoCloseable {

    public static final String ISSUER = "bankdash-auth";
    public static final String AUDIENCE = "bankdash-api";

    private final HttpServer server;
    private final AtomicInteger jwksRequests = new AtomicInteger();
    private volatile RSAKey key = newKey();

    private TestJwtIssuer(HttpServer server) {
        this.server = server;
    }

    public static TestJwtIssuer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            TestJwtIssuer issuer = new TestJwtIssuer(server);
            server.createContext("/.well-known/jwks.json", exchange -> {
                issuer.jwksRequests.incrementAndGet();
                byte[] body = new JWKSet(issuer.key).toPublicJWKSet().toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return issuer;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String jwksUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
    }

    /** How often the JWKS was fetched, e.g. to check caching or refetch on a new {@code kid}. */
    public int jwksRequests() {
        return jwksRequests.get();
    }

    /** A valid 15-minute access token for {@code subject}. */
    public String token(UUID subject) {
        return token(subject, claims -> {});
    }

    /** A token with the standard claims, then adjusted by {@code customizer}. */
    public String token(UUID subject, Consumer<JWTClaimsSet.Builder> customizer) {
        return sign(key, subject, customizer);
    }

    public String expiredToken(UUID subject) {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1));
        return token(subject, claims -> claims
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(Duration.ofMinutes(15)))));
    }

    /** Correct claims, but signed by a key that is not in the JWKS. */
    public String tokenSignedByUnknownKey(UUID subject) {
        return sign(newKey(), subject, claims -> {});
    }

    /** Replaces the signing key, as a restart of auth-service with an ephemeral key would. */
    public void rotateKey() {
        key = newKey();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static String sign(RSAKey key, UUID subject, Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(subject.toString())
                .claim("email", subject + "@example.com")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(15))))
                .jwtID(UUID.randomUUID().toString());
        customizer.accept(claims);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build();
        try {
            SignedJWT jwt = new SignedJWT(header, claims.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint(true).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
