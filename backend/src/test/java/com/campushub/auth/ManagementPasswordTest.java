package com.campushub.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class ManagementPasswordTest {
    @Test void pythonProvisioningPbkdf2GoldenVectorMatchesSpringEncoder() {
        // Python hashlib.pbkdf2_hmac('sha256', b'contract-password', bytes(range(16)), 600000, 32).
        String fixture="000102030405060708090a0b0c0d0e0f4561f78f8dca4c20eeee19121ee43c1530e6fab8fdf22b4e9e06758eecc74415";
        var encoder=new Pbkdf2PasswordEncoder("",16,600000,Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        assertThat(encoder.matches("contract-password",fixture)).isTrue();
        assertThat(encoder.matches("wrong-password",fixture)).isFalse();
    }
}
