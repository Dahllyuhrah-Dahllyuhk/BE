package org.dallyeo.matuabom.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class AesEncryptorTest {

    private AesEncryptor encryptor;

    @BeforeEach
    void setUp() {
        // 정확히 32바이트인 Base64 키 (matuabom-dev-only-key-32bytes!!!)
        String key = Base64.getEncoder().encodeToString("matuabom-dev-only-key-32bytes!!!".getBytes());
        encryptor = new AesEncryptor(key);
    }

    @Test
    @DisplayName("암호화 후 복호화하면 원문과 동일해야 함")
    void encryptDecryptRoundTrip() {
        String plainText = "test-refresh-token-value";

        String encrypted = encryptor.encrypt(plainText);
        String decrypted = encryptor.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plainText);
    }

    @Test
    @DisplayName("암호화 결과는 원문과 달라야 함")
    void encryptedDiffersFromPlainText() {
        String plainText = "secret-token";
        String encrypted = encryptor.encrypt(plainText);

        assertThat(encrypted).isNotEqualTo(plainText);
    }

    @Test
    @DisplayName("같은 원문이라도 암호화할 때마다 다른 값 (IV가 랜덤)")
    void sameInputProducesDifferentCiphertext() {
        String plainText = "same-token";
        String enc1 = encryptor.encrypt(plainText);
        String enc2 = encryptor.encrypt(plainText);

        assertThat(enc1).isNotEqualTo(enc2);
        // 하지만 복호화 결과는 같아야 함
        assertThat(encryptor.decrypt(enc1)).isEqualTo(plainText);
        assertThat(encryptor.decrypt(enc2)).isEqualTo(plainText);
    }

    @Test
    @DisplayName("null 입력 시 null 반환")
    void nullInputReturnsNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }
}
