package com.flowpilot.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore-backed AES-GCM cipher helper.
 * Encrypts/decrypts webhook secret strings at rest with authenticated encryption.
 * Ciphertext payload layout: [VERSION (1 byte)][IV (12 bytes)][CIPHERTEXT + 16-byte GCM TAG]
 * Encoded as prefix string "enc:v1:" + Base64(payload).
 */
object SecretCipher {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "flowpilot_webhook_secrets_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private const val PREFIX_V1 = "enc:v1:"
    private const val VERSION_BYTE: Byte = 1

    /**
     * Optional key provider for testing or custom keystore implementations.
     */
    internal var secretKeyProvider: (() -> SecretKey)? = null

    private fun getOrCreateSecretKey(): SecretKey {
        secretKeyProvider?.let { return it() }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts plaintext string if non-empty.
     * Returns empty string if plaintext is empty.
     * If input is already encrypted, returns it as-is.
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        if (isEncrypted(plaintext)) return plaintext

        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv ?: throw IllegalStateException("Cipher IV cannot be null")
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(1 + iv.size + encryptedBytes.size)
        buffer.put(VERSION_BYTE)
        buffer.put(iv)
        buffer.put(encryptedBytes)

        val encoded = Base64.getEncoder().encodeToString(buffer.array())
        return "$PREFIX_V1$encoded"
    }

    /**
     * Decrypts encrypted string.
     * If input does not start with prefix (i.e. legacy plaintext), returns plaintext as-is.
     * If decryption fails, returns empty string or fallback value to prevent crashes.
     */
    fun decrypt(text: String): String {
        if (text.isEmpty()) return ""
        if (!isEncrypted(text)) return text

        val payload = text.removePrefix(PREFIX_V1)
        val raw = try {
            Base64.getDecoder().decode(payload)
        } catch (_: Exception) {
            return text
        }

        if (raw.size <= 1 + IV_LENGTH_BYTES) {
            return text
        }

        val version = raw[0]
        if (version != VERSION_BYTE) {
            return text
        }

        val iv = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(raw, 1, iv, 0, IV_LENGTH_BYTES)

        val cipherBytes = ByteArray(raw.size - 1 - IV_LENGTH_BYTES)
        System.arraycopy(raw, 1 + IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.size)

        return try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            // Decryption failure (key lost, corrupted, device restored without keys)
            ""
        }
    }

    fun isEncrypted(text: String): Boolean {
        return text.startsWith(PREFIX_V1)
    }
}
