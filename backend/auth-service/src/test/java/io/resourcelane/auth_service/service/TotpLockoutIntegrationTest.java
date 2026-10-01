package io.resourcelane.auth_service.service;

import io.resourcelane.auth_service.model.User;
import io.resourcelane.auth_service.repository.AuthRepositoryWrapper;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs verification through the Spring proxy against a real database, to check that a failed
 * attempt is committed even though verification throws.
 */
@SpringBootTest
class TotpLockoutIntegrationTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private AuthRepositoryWrapper users;

    @Test
    void wrongCodesArePersistedAndLockTheAccount() {
        String secret = authService.generateSecretKey();
        Long userId = users.save(new User(null, "lockout", "lockout-" + System.nanoTime(), secret, "permanent"))
                .getUserId();
        String wrong = wrongCode(secret);

        for (int attempt = 1; attempt < AuthServiceImpl.MAX_FAILED_TOTP_ATTEMPTS; attempt++) {
            User fresh = users.findById(userId).orElseThrow();
            assertThatThrownBy(() -> authService.verifyTotp(secret, wrong, fresh))
                    .isInstanceOf(BadCredentialsException.class);
            assertThat(users.findById(userId).orElseThrow().getFailedTotpAttempts()).isEqualTo(attempt);
        }
        User beforeLock = users.findById(userId).orElseThrow();
        assertThatThrownBy(() -> authService.verifyTotp(secret, wrong, beforeLock))
                .isInstanceOf(BadCredentialsException.class);

        User locked = users.findById(userId).orElseThrow();
        assertThat(locked.getTotpLockedUntil()).isAfter(Instant.now());
        assertThatThrownBy(() -> authService.verifyTotp(secret, code(secret, step()), locked))
                .isInstanceOf(LockedException.class);
    }

    @Test
    void acceptedCodeIsRecordedAndCannotBeReplayed() {
        String secret = authService.generateSecretKey();
        Long userId = users.save(new User(null, "replay", "replay-" + System.nanoTime(), secret, "temporary"))
                .getUserId();
        String valid = code(secret, step());

        assertThat(authService.verifyTotp(secret, valid, users.findById(userId).orElseThrow())).isTrue();
        User stored = users.findById(userId).orElseThrow();
        assertThat(stored.getState()).isEqualTo("permanent");
        assertThat(stored.getLastTotpTimeStep()).isNotNull();

        assertThatThrownBy(() -> authService.verifyTotp(secret, valid, stored))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("TOTP code already used");
    }

    private static long step() {
        return Instant.now().getEpochSecond() / 30;
    }

    /** A code that is not valid in any accepted step around now. */
    private static String wrongCode(String secret) {
        long now = step();
        Set<String> accepted = Set.of(code(secret, now - 1), code(secret, now), code(secret, now + 1),
                code(secret, now + 2));
        return Stream.of("000000", "111111", "222222", "333333", "444444", "555555")
                .filter(candidate -> !accepted.contains(candidate))
                .findFirst().orElseThrow();
    }

    private static String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(new Base32().decode(secret), "HmacSHA1"));
            byte[] data = new byte[8];
            long value = step;
            for (int i = 7; value > 0; i--) {
                data[i] = (byte) (value & 0xFF);
                value >>= 8;
            }
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1000000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
