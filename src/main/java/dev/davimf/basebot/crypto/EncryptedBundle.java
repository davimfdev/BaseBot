package dev.davimf.basebot.crypto;

/**
 * The encrypted transcript payload, field-for-field matching what the davimf.dev
 * dashboard expects (see netlify/functions/ticket-store.ts and src/lib/ticketCrypto.ts).
 *
 * <p>All binary fields are Base64 (standard, with padding). {@code iterations} is the
 * PBKDF2 work factor used for BOTH the key derivation and the password verifier.
 */
public record EncryptedBundle(
        String saltKey,       // base64 — salt for the AES key derivation
        String saltHash,      // base64 — salt for the password verifier
        String iv,            // base64 — 12-byte AES-GCM nonce
        String ciphertext,    // base64 — AES-GCM ciphertext WITH appended 128-bit tag
        String passwordHash,  // base64 — PBKDF2(password, saltHash)
        int iterations
) {}
