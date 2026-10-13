package com.bankdash.auth.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public keys for validating access tokens. The gateway and every service fetch this. */
@RestController
class JwksController {

    private final Map<String, Object> jwks;

    JwksController(RSAKey signingKey) {
        // toPublicJWKSet() drops the private parts.
        this.jwks = new JWKSet(signingKey).toPublicJWKSet().toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        return jwks;
    }
}
