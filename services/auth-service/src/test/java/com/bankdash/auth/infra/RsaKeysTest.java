package com.bankdash.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RsaKeysTest {

    @Test
    void generatesA2048BitSigningKeyWithThumbprintKid() throws Exception {
        RSAKey key = RsaKeys.generate();

        assertThat(key.size()).isEqualTo(2048);
        assertThat(key.isPrivate()).isTrue();
        assertThat(key.getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
        assertThat(key.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(key.getKeyID()).isEqualTo(key.computeThumbprint().toString());
    }

    @Test
    void loadsTheSameKeyFromPkcs8Pem() throws Exception {
        RSAKey original = RsaKeys.generate();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(original.toPrivateKey().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        RSAKey loaded = RsaKeys.fromPkcs8Pem(pem);

        assertThat(loaded.getModulus()).isEqualTo(original.getModulus());
        assertThat(loaded.getKeyID()).isEqualTo(original.getKeyID());
        assertThat(loaded.isPrivate()).isTrue();
    }

    @Test
    void rejectsSomethingThatIsNotAPrivateKey() {
        assertThatIllegalArgumentException().isThrownBy(() -> RsaKeys.fromPkcs8Pem("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"));
    }
}
