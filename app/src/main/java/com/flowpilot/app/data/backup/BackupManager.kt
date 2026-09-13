package com.flowpilot.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.flowpilot.app.BuildConfig
import com.flowpilot.app.data.model.Automation
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class FlowPilotBackup(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val automations: List<Automation> = emptyList(),
) {
    companion object {
        const val BACKUP_VERSION = 1
    }
}

@Serializable
data class FlowPilotEncryptedBackup(
    val format: String = ENCRYPTED_BACKUP_FORMAT,
    val version: Int = ENCRYPTED_BACKUP_VERSION,
    val iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    val salt: String = "",
    val iv: String = "",
    val ciphertext: String = "",
    val data: String = "",
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = BuildConfig.VERSION_NAME,
) {
    companion object {
        const val ENCRYPTED_BACKUP_FORMAT = "flowpilot_encrypted_backup"
        const val ENCRYPTED_BACKUP_VERSION = 1
        const val DEFAULT_PBKDF2_ITERATIONS = 100_000
        const val MIN_PBKDF2_ITERATIONS = 10_000
        const val MAX_PBKDF2_ITERATIONS = 500_000
    }
}

typealias EncryptedBackupEnvelope = FlowPilotEncryptedBackup

enum class ImportStrategy {
    MERGE,
    REPLACE_ALL,
}

object BackupManager {

    const val MIN_PASSWORD_LENGTH = 6
    const val ENCRYPTED_BACKUP_FORMAT = FlowPilotEncryptedBackup.ENCRYPTED_BACKUP_FORMAT
    const val ENCRYPTED_BACKUP_VERSION = FlowPilotEncryptedBackup.ENCRYPTED_BACKUP_VERSION
    const val DEFAULT_PBKDF2_ITERATIONS = FlowPilotEncryptedBackup.DEFAULT_PBKDF2_ITERATIONS
    const val MIN_PBKDF2_ITERATIONS = FlowPilotEncryptedBackup.MIN_PBKDF2_ITERATIONS
    const val MAX_PBKDF2_ITERATIONS = FlowPilotEncryptedBackup.MAX_PBKDF2_ITERATIONS

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16

    private val supportedEncryptedFormats = setOf(
        ENCRYPTED_BACKUP_FORMAT,
        "flowpilot-encrypted-backup",
        "flowpilot_encrypted",
    )

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val parserJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToString(automations: List<Automation>): String {
        val safeRules = automations.map { it.copy(webhookUrl = "", webhookHeaders = "", webhookBody = "") }
        val backup = FlowPilotBackup(
            version = FlowPilotBackup.BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            automations = safeRules,
        )
        return prettyJson.encodeToString(FlowPilotBackup.serializer(), backup)
    }

    fun exportSingleToString(rule: Automation): String {
        val safeRule = rule.copy(webhookUrl = "", webhookHeaders = "", webhookBody = "")
        return prettyJson.encodeToString(Automation.serializer(), safeRule)
    }

    fun isValidPassword(password: CharSequence?): Boolean =
        password != null && password.length >= MIN_PASSWORD_LENGTH

    fun isValidPassword(password: CharArray?): Boolean =
        password != null && password.size >= MIN_PASSWORD_LENGTH

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun exportEncryptedToString(
        automations: List<Automation>,
        password: CharArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    ): String {
        if (!isValidPassword(password)) {
            throw IllegalArgumentException("Password must be at least $MIN_PASSWORD_LENGTH characters")
        }
        val fullRules = automations.map { it.withDecryptedSecrets() }
        val backup = FlowPilotBackup(
            version = FlowPilotBackup.BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            automations = fullRules,
        )
        val plaintext = prettyJson.encodeToString(FlowPilotBackup.serializer(), backup)
        return encryptPayload(plaintext, password, iterations)
    }

    fun exportEncryptedToString(
        automations: List<Automation>,
        password: String,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    ): String {
        val chars = password.toCharArray()
        return try {
            exportEncryptedToString(automations, chars, iterations)
        } finally {
            chars.fill('\u0000')
        }
    }

    fun exportEncryptedSingleToString(
        rule: Automation,
        password: CharArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    ): String {
        if (!isValidPassword(password)) {
            throw IllegalArgumentException("Password must be at least $MIN_PASSWORD_LENGTH characters")
        }
        val fullRule = rule.withDecryptedSecrets()
        val plaintext = prettyJson.encodeToString(Automation.serializer(), fullRule)
        return encryptPayload(plaintext, password, iterations)
    }

    fun exportEncryptedSingleToString(
        rule: Automation,
        password: String,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    ): String {
        val chars = password.toCharArray()
        return try {
            exportEncryptedSingleToString(rule, chars, iterations)
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun encryptPayload(
        plaintext: String,
        password: CharArray,
        iterations: Int,
    ): String {
        require(iterations in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
            "Iteration count out of allowed range: $iterations"
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { random.nextBytes(this) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).apply { random.nextBytes(this) }

        val secretKey = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val ciphertextBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val saltBase64 = Base64.getEncoder().encodeToString(salt)
        val ivBase64 = Base64.getEncoder().encodeToString(iv)
        val ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertextBytes)

        val envelope = FlowPilotEncryptedBackup(
            format = ENCRYPTED_BACKUP_FORMAT,
            version = ENCRYPTED_BACKUP_VERSION,
            iterations = iterations,
            salt = saltBase64,
            iv = ivBase64,
            ciphertext = ciphertextBase64,
            data = ciphertextBase64,
            exportedAt = System.currentTimeMillis(),
        )
        return prettyJson.encodeToString(FlowPilotEncryptedBackup.serializer(), envelope)
    }

    fun parseEncryptedImport(
        jsonContent: String,
        password: CharArray,
    ): Result<List<Automation>> {
        if (!isValidPassword(password)) {
            return Result.failure(IllegalArgumentException("Password must be at least $MIN_PASSWORD_LENGTH characters"))
        }
        return decryptPayload(jsonContent, password)
    }

    fun parseEncryptedImport(
        jsonContent: String,
        password: String,
    ): Result<List<Automation>> {
        val chars = password.toCharArray()
        return try {
            parseEncryptedImport(jsonContent, chars)
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun decryptPayload(
        jsonContent: String,
        password: CharArray,
    ): Result<List<Automation>> {
        val trimmed = jsonContent.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty content"))
        }

        val envelope = try {
            parserJson.decodeFromString(FlowPilotEncryptedBackup.serializer(), trimmed)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid encrypted backup format", e))
        }

        if (envelope.format !in supportedEncryptedFormats) {
            return Result.failure(IllegalArgumentException("Unsupported backup format: ${envelope.format}"))
        }

        if (envelope.version != ENCRYPTED_BACKUP_VERSION) {
            return Result.failure(IllegalArgumentException("Unsupported backup version: ${envelope.version}"))
        }

        if (envelope.iterations !in MIN_PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) {
            return Result.failure(IllegalArgumentException("Iteration count out of allowed range: ${envelope.iterations}"))
        }

        val cipherBase64 = envelope.ciphertext.ifBlank { envelope.data }
        if (cipherBase64.isBlank() || envelope.salt.isBlank() || envelope.iv.isBlank()) {
            return Result.failure(IllegalArgumentException("Corrupted backup envelope: missing cryptographic fields"))
        }

        val salt = try {
            Base64.getDecoder().decode(envelope.salt)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Corrupted salt encoding", e))
        }

        val iv = try {
            Base64.getDecoder().decode(envelope.iv)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Corrupted IV encoding", e))
        }

        val ciphertext = try {
            Base64.getDecoder().decode(cipherBase64)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Corrupted ciphertext encoding", e))
        }

        val secretKey = try {
            deriveKey(password, salt, envelope.iterations)
        } catch (e: Exception) {
            return Result.failure(SecurityException("Key derivation failed", e))
        }

        val plaintextBytes = try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            return Result.failure(SecurityException("Invalid password or corrupted backup payload", e))
        } catch (e: GeneralSecurityException) {
            return Result.failure(SecurityException("Decryption failed: invalid password or tampered data", e))
        } catch (e: Exception) {
            return Result.failure(SecurityException("Decryption failed", e))
        }

        val plaintext = String(plaintextBytes, Charsets.UTF_8)
        return parseDecryptedContent(plaintext)
    }

    private fun parseDecryptedContent(plaintext: String): Result<List<Automation>> {
        val trimmed = plaintext.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Decrypted content is empty"))
        }

        // 1. Try parsing as full FlowPilotBackup
        try {
            val backup = parserJson.decodeFromString(FlowPilotBackup.serializer(), trimmed)
            if (backup.automations.isNotEmpty() || trimmed.contains("\"automations\"")) {
                return Result.success(backup.automations)
            }
        } catch (_: Throwable) {}

        // 2. Try parsing as List<Automation>
        try {
            val list = parserJson.decodeFromString(ListSerializer(Automation.serializer()), trimmed)
            return Result.success(list)
        } catch (_: Throwable) {}

        // 3. Try parsing as single Automation
        try {
            val single = parserJson.decodeFromString(Automation.serializer(), trimmed)
            return Result.success(listOf(single))
        } catch (_: Throwable) {}

        return Result.failure(IllegalArgumentException("Invalid decrypted automation format"))
    }

    fun isEncryptedBackup(jsonContent: String): Boolean {
        val trimmed = jsonContent.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val envelope = parserJson.decodeFromString(FlowPilotEncryptedBackup.serializer(), trimmed)
            envelope.format in supportedEncryptedFormats &&
                envelope.salt.isNotBlank() &&
                envelope.iv.isNotBlank() &&
                (envelope.ciphertext.isNotBlank() || envelope.data.isNotBlank())
        } catch (_: Throwable) {
            false
        }
    }

    private fun disableImportedRules(rules: List<Automation>): List<Automation> =
        rules.map { it.copy(enabled = false) }

    fun parseImport(jsonContent: String): Result<List<Automation>> {
        val trimmed = jsonContent.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty content"))
        }

        if (isEncryptedBackup(trimmed)) {
            return Result.failure(IllegalArgumentException("Backup is encrypted; use encrypted restore with password"))
        }

        // 1. Try parsing as full FlowPilotBackup
        try {
            val backup = parserJson.decodeFromString(FlowPilotBackup.serializer(), trimmed)
            if (backup.automations.isNotEmpty()) {
                return Result.success(disableImportedRules(backup.automations))
            }
        } catch (_: Throwable) {}

        // 2. Try parsing as List<Automation>
        try {
            val list = parserJson.decodeFromString(ListSerializer(Automation.serializer()), trimmed)
            if (list.isNotEmpty()) {
                return Result.success(disableImportedRules(list))
            }
        } catch (_: Throwable) {}

        // 3. Try parsing as single Automation
        try {
            val single = parserJson.decodeFromString(Automation.serializer(), trimmed)
            return Result.success(listOf(single.copy(enabled = false)))
        } catch (_: Throwable) {}

        return Result.failure(IllegalArgumentException("Invalid JSON format for automations"))
    }

    fun prepareShareFile(context: Context, fileName: String, content: String): Uri {
        val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(shareDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun generateBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "flowpilot_backup_$dateStr.json"
    }

    fun generateEncryptedBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "flowpilot_encrypted_backup_$dateStr.json"
    }

    fun generateRuleFileName(ruleName: String): String {
        val safeName = ruleName.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(30)
            .ifBlank { "rule" }
        return "flowpilot_$safeName.json"
    }

    fun generateEncryptedRuleFileName(ruleName: String): String {
        val safeName = ruleName.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(30)
            .ifBlank { "rule" }
        return "flowpilot_encrypted_$safeName.json"
    }
}
