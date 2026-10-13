package com.bankdash.auth.app;

import com.nimbusds.jose.jwk.RSAKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues short-lived RS256 access tokens. */
@Component
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final String keyId;
    private final AuthProperties properties;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder encoder, RSAKey signingKey, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.keyId = signingKey.getKeyID();
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(UUID userId, String email) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(userId.toString())
                .claim("email", email)
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, properties.accessTokenTtl());
    }

    public record AccessToken(String value, Duration expiresIn) {}
}
