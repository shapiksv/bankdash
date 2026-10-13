package com.bankdash.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.testsupport.ContainersConfig;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class JwksIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    RSAKey signingKey;

    @Test
    void publishesOnlyThePublicSigningKey() {
        var response = mvc.get().uri("/.well-known/jwks.json").exchange();

        assertThat(response).hasStatus(HttpStatus.OK);
        assertThat(response).bodyJson().extractingPath("$.keys.length()").isEqualTo(1);
        assertThat(response).bodyJson().extractingPath("$.keys[0].kid").isEqualTo(signingKey.getKeyID());
        assertThat(response).bodyJson().extractingPath("$.keys[0].kty").isEqualTo("RSA");
        assertThat(response).bodyJson().extractingPath("$.keys[0].alg").isEqualTo("RS256");
        assertThat(response).bodyJson().extractingPath("$.keys[0].use").isEqualTo("sig");
        assertThat(response).bodyJson().doesNotHavePath("$.keys[0].d");
        assertThat(response).bodyJson().doesNotHavePath("$.keys[0].p");
    }

    @Test
    void protectedEndpointsAnswer401AsProblemDetail() {
        var response = mvc.get().uri("/auth/me").exchange();

        assertThat(response).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(response).containsHeader("WWW-Authenticate");
        assertThat(response).hasContentType("application/problem+json");
        assertThat(response).bodyJson().extractingPath("$.status").isEqualTo(401);
    }
}
