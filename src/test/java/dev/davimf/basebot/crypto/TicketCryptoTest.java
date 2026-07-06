// [OUTLINE START]
// Package: dev.davimf.basebot.crypto
// 
// Class: TicketCryptoTest
// 
// Methods:
//   - `Method` : `private static String decrypt(EncryptedBundle b, String password)`
//   - `Method` : `private static byte[] pbkdf2(String password, byte[] salt, int iterations)`
//   - `Method` : `private static byte[] b64(String s)`
// 
// Fields:
//   - `Field` : `private static final int ITER`
// [OUTLINE END]



package dev.davimf.basebot.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies {@link TicketCrypto} produces a bundle decryptable with the exact algorithm
 * the davimf.dev dashboard uses (PBKDF2-HMAC-SHA256 -> AES-256-GCM). The decrypt logic
 * here mirrors {@code src/lib/ticketCrypto.ts}; if it round-trips, the browser will too.
 */
class TicketCryptoTest {

    private static final int ITER = 50_000; // low for fast tests; prod uses 210k

    @Test
    void roundTripsWithGeneratedPassword() throws Exception {
        TicketCrypto crypto = new TicketCrypto(ITER);
        String plaintext = "{\"messages\":[{\"a\":\"olá çãé 😀\"}]}";

        TicketCrypto.Result result = crypto.encrypt(plaintext);
        EncryptedBundle b = result.bundle();

        // Password verifier must match PBKDF2(password, saltHash).
        byte[] expectedVerifier = pbkdf2(result.password(), b64(b.saltHash()), b.iterations());
        assertArrayEquals(expectedVerifier, b64(b.passwordHash()), "password verifier mismatch");

        // Decrypt exactly as WebCrypto would.
        String decrypted = decrypt(b, result.password());
        assertEquals(plaintext, decrypted, "round-trip plaintext mismatch");
    }

    @Test
    void usesDistinctSaltsForKeyAndVerifier() {
        EncryptedBundle b = new TicketCrypto(ITER).encrypt("x").bundle();
        assertNotEquals(b.saltKey(), b.saltHash(), "key and verifier salts must differ");
    }

    private static String decrypt(EncryptedBundle b, String password) throws Exception {
        byte[] keyBits = pbkdf2(password, b64(b.saltKey()), b.iterations());
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBits, "AES"),
                new GCMParameterSpec(128, b64(b.iv())));
        byte[] plain = cipher.doFinal(b64(b.ciphertext()));
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return f.generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 256)).getEncoded();
    }

    private static byte[] b64(String s) {
        return Base64.getDecoder().decode(s);
    }
}
