package dev.davimf.basebot.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Produces {@link EncryptedBundle}s that the davimf.dev dashboard can decrypt in the
 * browser via WebCrypto. The algorithm here is the exact inverse of
 * {@code src/lib/ticketCrypto.ts}:
 *
 * <ul>
 *   <li>PBKDF2 (HMAC-SHA-256), {@code iterations} rounds, 256-bit output.</li>
 *   <li>Encryption key = PBKDF2(password, saltKey); data = AES-256-<b>GCM</b>(iv).</li>
 *   <li>Password verifier = PBKDF2(password, saltHash) stored as {@code passwordHash}.</li>
 *   <li>WebCrypto appends the 128-bit GCM tag to the ciphertext; JCE's
 *       {@code AES/GCM/NoPadding} does the same, so the bytes line up.</li>
 * </ul>
 *
 * <p>Two independent salts are used (one for the key, one for the verifier) exactly as
 * the dashboard expects; reusing a single salt would fail decryption.
 */
public final class TicketCrypto {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;      // GCM standard nonce length
    private static final int TAG_BITS = 128;     // matches WebCrypto default

    private final int iterations;

    public TicketCrypto(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be >= 1");
        }
        this.iterations = iterations;
    }

    /**
     * Encrypts {@code plaintext} (the transcript JSON) under a freshly generated random
     * password and returns both the password (to show the user) and the bundle (to POST).
     */
    public Result encrypt(String plaintext) {
        return encrypt(plaintext, generatePassword());
    }

    public Result encrypt(String plaintext, String password) {
        try {
            byte[] saltKey = randomBytes(SALT_BYTES);
            byte[] saltHash = randomBytes(SALT_BYTES);
            byte[] iv = randomBytes(IV_BYTES);

            byte[] keyBits = pbkdf2(password, saltKey, iterations);
            byte[] verifier = pbkdf2(password, saltHash, iterations);

            SecretKey key = new SecretKeySpec(keyBits, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder b64 = Base64.getEncoder();
            EncryptedBundle bundle = new EncryptedBundle(
                    b64.encodeToString(saltKey),
                    b64.encodeToString(saltHash),
                    b64.encodeToString(iv),
                    b64.encodeToString(ciphertext),
                    b64.encodeToString(verifier),
                    iterations
            );
            return new Result(password, bundle);
        } catch (Exception e) {
            throw new IllegalStateException("Ticket transcript encryption failed", e);
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return f.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    /** URL-safe, human-shareable random password (no ambiguous characters). */
    public static String generatePassword() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Encryption output: the plaintext password (shown once) and the bundle to persist. */
    public record Result(String password, EncryptedBundle bundle) {}
}
