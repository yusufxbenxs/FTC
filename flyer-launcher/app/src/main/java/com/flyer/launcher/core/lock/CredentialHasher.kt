package com.flyer.launcher.core.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes the launcher-only PIN/password with PBKDF2-HMAC-SHA256 and a random salt.
 * Only the salt and hash are stored; comparison is constant-time.
 */
object CredentialHasher {
    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    data class Hashed(val salt: String, val hash: String, val iterations: Int)

    fun hash(secret: String, iterations: Int = ITERATIONS, salt: ByteArray = randomSalt()): Hashed =
        Hashed(b64(salt), b64(derive(secret, salt, iterations)), iterations)

    fun verify(secret: String, stored: Hashed): Boolean {
        val salt = runCatching { Base64.getDecoder().decode(stored.salt) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(stored.hash) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(secret, salt, stored.iterations), expected)
    }

    /** PINs are 4–12 digits; passwords are 4–64 characters. Returns an error message or null. */
    fun validate(secret: String, pin: Boolean): String? = when {
        pin && !secret.all { it.isDigit() } -> "A PIN can only contain digits"
        pin && secret.length !in 4..12 -> "Use 4 to 12 digits"
        !pin && secret.length !in 4..64 -> "Use 4 to 64 characters"
        else -> null
    }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
}
