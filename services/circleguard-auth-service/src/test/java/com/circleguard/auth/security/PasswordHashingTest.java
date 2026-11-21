package com.circleguard.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-24: costo de BCrypt de las contraseñas locales (mejora de rendimiento del login, sin bajar de OWASP). */
class PasswordHashingTest {

    private static final String V7 = "db/migration/V7__rehash_seed_users_bcrypt_cost_10.sql";

    @Test
    void seedAccountsAreRehashedWithCost10AndStillMatchTheirPassword() throws IOException {
        String hash = hashIn(V7);

        assertThat(hash).startsWith("$2a$10$");
        assertThat(BCrypt.checkpw("password", hash)).isTrue();
        assertThat(BCrypt.checkpw("otra", hash)).isFalse();
    }

    @Test
    void encoderUsesTheConfiguredCostForNewPasswords() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder(10);

        String hash = encoder.encode("password");
        assertThat(hash).startsWith("$2a$10$");
        assertThat(encoder.matches("password", hash)).isTrue();
    }

    @Test
    void encoderStillVerifiesOlderCost12Hashes() {
        // Un hash de costo 12 (como los que dejó V3) se sigue aceptando: BCrypt lee el costo del propio hash.
        String cost12 = BCrypt.hashpw("password", BCrypt.gensalt(12));

        assertThat(new SecurityConfig().passwordEncoder(10).matches("password", cost12)).isTrue();
    }

    @Test
    void costBelowTheOwaspMinimumIsRejected() {
        assertThatThrownBy(() -> new SecurityConfig().passwordEncoder(9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos 10");
    }

    private static String hashIn(String resource) throws IOException {
        try (InputStream in = PasswordHashingTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("'(\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53})'").matcher(sql);
            assertThat(m.find()).as("hash BCrypt en " + resource).isTrue();
            return m.group(1);
        }
    }
}
