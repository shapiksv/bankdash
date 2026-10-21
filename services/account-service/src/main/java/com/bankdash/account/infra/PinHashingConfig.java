package com.bankdash.account.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Card PINs are stored as BCrypt hashes only (docs/architecture.md section 6). */
@Configuration(proxyBeanMethods = false)
class PinHashingConfig {

    @Bean
    PasswordEncoder pinEncoder() {
        return new BCryptPasswordEncoder();
    }
}
