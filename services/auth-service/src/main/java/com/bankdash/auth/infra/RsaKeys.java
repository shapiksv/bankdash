package com.bankdash.auth.infra;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/** Creates the RS256 signing key. The key id ({@code kid}) is always the JWK thumbprint. */
public final class RsaKeys {

    private RsaKeys() {}

    /** A fresh 2048-bit key, for local development and tests. */
    public static RSAKey generate() {
        try {
            return withThumbprintKid(new RSAKeyGenerator(2048).generate());
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot generate an RSA key", e);
        }
    }

    /** Reads an unencrypted PKCS#8 PEM ({@code -----BEGIN PRIVATE KEY-----}). */
    public static RSAKey fromPkcs8Pem(String pem) {
        String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var privateKey = (RSAPrivateCrtKey)
                    factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            var publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return withThumbprintKid(new RSAKey.Builder(publicKey).privateKey(privateKey).build());
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalArgumentException("Not an unencrypted PKCS#8 RSA private key", e);
        }
    }

    private static RSAKey withThumbprintKid(RSAKey key) {
        try {
            return new RSAKey.Builder(key)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot compute the JWK thumbprint", e);
        }
    }
}
