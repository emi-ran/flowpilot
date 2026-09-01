package com.flowpilot.app.data.security

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecretCipherTest {

    private lateinit var testSecretKey: SecretKey

    @Before
    fun setup() {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        testSecretKey = keyGen.generateKey()
        SecretCipher.secretKeyProvider = { testSecretKey }
    }

    @After
    fun tearDown() {
        SecretCipher.secretKeyProvider = null
    }

    @Test
    fun encryptAndDecrypt_preservesPlainText() {
        val originalText = "Authorization: Bearer my_secret_token_12345\nContent-Type: application/json"
        val encrypted = SecretCipher.encrypt(originalText)

        assertThat(encrypted).startsWith("enc:v1:")
        assertThat(encrypted).isNotEqualTo(originalText)
        assertThat(SecretCipher.isEncrypted(encrypted)).isTrue()

        val decrypted = SecretCipher.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(originalText)
    }

    @Test
    fun encrypt_emptyString_returnsEmptyString() {
        assertThat(SecretCipher.encrypt("")).isEqualTo("")
        assertThat(SecretCipher.decrypt("")).isEqualTo("")
    }

    @Test
    fun encrypt_alreadyEncrypted_idempotent() {
        val original = "https://user:password@example.com/api?token=secret123"
        val encrypted1 = SecretCipher.encrypt(original)
        val encrypted2 = SecretCipher.encrypt(encrypted1)

        assertThat(encrypted2).isEqualTo(encrypted1)
        assertThat(SecretCipher.decrypt(encrypted2)).isEqualTo(original)
    }

    @Test
    fun decrypt_legacyPlainText_returnsUnmodified() {
        val legacyUrl = "https://api.example.com/webhook"
        assertThat(SecretCipher.decrypt(legacyUrl)).isEqualTo(legacyUrl)

        val legacyHeaders = "X-Api-Key: secret123"
        assertThat(SecretCipher.decrypt(legacyHeaders)).isEqualTo(legacyHeaders)
    }

    @Test
    fun decrypt_corruptOrDifferentKey_returnsEmptyGracefully() {
        val original = "SuperSecretPayload"
        val encrypted = SecretCipher.encrypt(original)

        // Switch to a different key to simulate keystore reset / device migration without keystore
        val otherKeyGen = KeyGenerator.getInstance("AES")
        otherKeyGen.init(256)
        SecretCipher.secretKeyProvider = { otherKeyGen.generateKey() }

        val decrypted = SecretCipher.decrypt(encrypted)
        // Must fail gracefully and return empty string without crash
        assertThat(decrypted).isEmpty()
    }

    @Test
    fun automation_withEncryptedAndDecryptedSecrets_roundTrip() {
        val rule = Automation(
            id = UUID.randomUUID().toString(),
            name = "Webhook Test",
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            action = ActionType.HTTP_WEBHOOK,
            actions = listOf(ActionType.HTTP_WEBHOOK),
            webhookUrl = "https://user:secretpass@api.service.com/notify?auth=tok123",
            webhookHeaders = "Authorization: Bearer topsecret\nX-Custom: value",
            webhookBody = "{\"key\": \"private_value\"}",
            webhookTimeoutSeconds = 15,
            createdAt = 1000L,
        )

        val encryptedRule = rule.withEncryptedSecrets()
        assertThat(encryptedRule.webhookUrl).startsWith("enc:v1:")
        assertThat(encryptedRule.webhookHeaders).startsWith("enc:v1:")
        assertThat(encryptedRule.webhookBody).startsWith("enc:v1:")
        assertThat(encryptedRule.webhookUrl).doesNotContain("secretpass")
        assertThat(encryptedRule.webhookHeaders).doesNotContain("topsecret")
        assertThat(encryptedRule.webhookBody).doesNotContain("private_value")

        val decryptedRule = encryptedRule.withDecryptedSecrets()
        assertThat(decryptedRule.webhookUrl).isEqualTo(rule.webhookUrl)
        assertThat(decryptedRule.webhookHeaders).isEqualTo(rule.webhookHeaders)
        assertThat(decryptedRule.webhookBody).isEqualTo(rule.webhookBody)
        assertThat(decryptedRule).isEqualTo(rule)
    }

    @Test
    fun automation_withDecryptedSecrets_handlesLegacyPlaintext() {
        val legacyRule = Automation(
            id = "legacy-1",
            name = "Legacy Webhook",
            triggerEvent = TriggerEvent.CHARGER_CONNECTED,
            action = ActionType.HTTP_WEBHOOK,
            webhookUrl = "https://legacy.example.com/api",
            webhookHeaders = "Content-Type: application/json",
            webhookBody = "{\"legacy\": true}",
            createdAt = 1000L,
        )

        val decrypted = legacyRule.withDecryptedSecrets()
        assertThat(decrypted.webhookUrl).isEqualTo("https://legacy.example.com/api")
        assertThat(decrypted.webhookHeaders).isEqualTo("Content-Type: application/json")
        assertThat(decrypted.webhookBody).isEqualTo("{\"legacy\": true}")
    }
}
