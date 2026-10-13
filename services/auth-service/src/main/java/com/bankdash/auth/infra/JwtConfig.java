package com.bankdash.auth.infra;

import com.bankdash.auth.app.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RSAKey signingKey(AuthProperties properties) throws IOException {
        if (properties.privateKey() != null) {
            RSAKey key = RsaKeys.fromPkcs8Pem(properties.privateKey().getContentAsString(StandardCharsets.UTF_8));
            log.info("Loaded the JWT signing key {} from {}", key.getKeyID(), properties.privateKey());
            return key;
        }
        RSAKey key = RsaKeys.generate();
        log.warn("bankdash.auth.private-key is not set: generated an ephemeral JWT signing key {}. "
                + "Access tokens become invalid when this service restarts.", key.getKeyID());
        return key;
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /** Validates access tokens for this service's own protected endpoints, such as {@code /auth/me}. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, AuthProperties properties) throws JOSEException {
        return accessTokenDecoder(signingKey, properties.issuer(), properties.audience());
    }

    /** Signature, {@code exp}/{@code nbf} (60 s clock skew), {@code iss} and {@code aud}. */
    static NimbusJwtDecoder accessTokenDecoder(RSAKey key, String issuer, String audience) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience))));
        return decoder;
    }
}
