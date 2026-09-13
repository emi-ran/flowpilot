package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.flowpilot.app.data.backup.BackupManager
import com.flowpilot.app.data.backup.FlowPilotBackup
import com.flowpilot.app.data.backup.FlowPilotEncryptedBackup
import com.flowpilot.app.data.backup.ImportStrategy
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.security.SecretCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var repository: AutomationRepository
    private lateinit var testSecretKey: SecretKey

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        testSecretKey = keyGen.generateKey()
        SecretCipher.secretKeyProvider = { testSecretKey }
        repository = AutomationRepository(context)
    }

    @After
    fun tearDown() = runTest {
        repository.rawDataStore.edit { it.clear() }
        SecretCipher.secretKeyProvider = null
    }

    private fun createSampleRule(id: String, name: String, webhookSecret: String = ""): Automation {
        return Automation(
            id = id,
            name = name,
            enabled = true,
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            batteryLevel = 25,
            actions = listOf(ActionType.BATTERY_SAVER_ON),
            webhookUrl = webhookSecret,
            createdAt = 1000L,
        )
    }

    @Test
    fun exportToString_createsValidJsonWithBackupMetadata() {
        val rules = listOf(
            createSampleRule("r1", "Low Battery Mode"),
            createSampleRule("r2", "Night Routine")
        )

        val json = BackupManager.exportToString(rules)
        assertThat(json).contains("\"version\":")
        assertThat(json).contains("\"automations\":")
        assertThat(json).contains("Low Battery Mode")
        assertThat(json).contains("Night Routine")

        val parsedResult = BackupManager.parseImport(json)
        assertThat(parsedResult.isSuccess).isTrue()
        val parsedRules = parsedResult.getOrNull()
        assertThat(parsedRules).isNotNull()
        assertThat(parsedRules).hasSize(2)
        assertThat(parsedRules!![0].name).isEqualTo("Low Battery Mode")
        assertThat(parsedRules[1].name).isEqualTo("Night Routine")
    }

    @Test
    fun exportToString_omitsWebhookSecrets() {
        val rule = createSampleRule("r-secret", "Secret Rule", "https://secret.example")
            .copy(webhookHeaders = "Authorization: Bearer secret", webhookBody = "secret body")

        val json = BackupManager.exportToString(listOf(rule))

        assertThat(json).doesNotContain("https://secret.example")
        assertThat(json).doesNotContain("Authorization: Bearer secret")
        assertThat(json).doesNotContain("secret body")
        assertThat(BackupManager.parseImport(json).getOrThrow().single().webhookUrl).isEmpty()
    }

    @Test
    fun exportSingleToString_omitsWebhookSecrets() {
        val rule = createSampleRule("r-single-secret", "Single Secret", "https://secret.example")
            .copy(webhookHeaders = "X-Secret: value", webhookBody = "secret body")

        val json = BackupManager.exportSingleToString(rule)

        assertThat(json).doesNotContain("https://secret.example")
        assertThat(json).doesNotContain("X-Secret: value")
        assertThat(json).doesNotContain("secret body")
    }

    @Test
    fun parseImport_disablesRulesFromFullBackupRawListAndSingleRule() {
        val enabledRule = createSampleRule("r-import", "Imported", "secret")
        val fullBackup = kotlinx.serialization.json.Json.encodeToString(
            FlowPilotBackup.serializer(), FlowPilotBackup(automations = listOf(enabledRule))
        )
        val rawList = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Automation.serializer()), listOf(enabledRule)
        )
        val single = kotlinx.serialization.json.Json.encodeToString(Automation.serializer(), enabledRule)

        assertThat(BackupManager.parseImport(fullBackup).getOrThrow().single().enabled).isFalse()
        assertThat(BackupManager.parseImport(rawList).getOrThrow().single().enabled).isFalse()
        assertThat(BackupManager.parseImport(single).getOrThrow().single().enabled).isFalse()
    }

    @Test
    fun exportSingleToString_serializesSingleRule() {
        val rule = createSampleRule("r-single", "Single Test")
        val json = BackupManager.exportSingleToString(rule)

        assertThat(json).contains("\"id\": \"r-single\"")
        assertThat(json).contains("\"name\": \"Single Test\"")

        val parsed = BackupManager.parseImport(json)
        assertThat(parsed.isSuccess).isTrue()
        assertThat(parsed.getOrNull()).hasSize(1)
        assertThat(parsed.getOrNull()!!.first().id).isEqualTo("r-single")
    }

    @Test
    fun parseImport_acceptsRawListFormat() {
        val rules = listOf(createSampleRule("r-list", "Raw List Rule"))
        val plainListJson = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Automation.serializer()),
            rules
        )

        val parsed = BackupManager.parseImport(plainListJson)
        assertThat(parsed.isSuccess).isTrue()
        assertThat(parsed.getOrNull()).hasSize(1)
        assertThat(parsed.getOrNull()!!.first().name).isEqualTo("Raw List Rule")
    }

    @Test
    fun parseImport_rejectsCorruptedOrEmptyJson() {
        val emptyResult = BackupManager.parseImport("   ")
        assertThat(emptyResult.isFailure).isTrue()

        val invalidResult = BackupManager.parseImport("{ not valid json at all")
        assertThat(invalidResult.isFailure).isTrue()
    }

    @Test
    fun importAutomations_mergeStrategy_appendsAndAssignsNewIds() = runTest {
        // Initial rule in repo
        repository.add(
            name = "Existing Rule",
            triggerEvent = TriggerEvent.DEVICE_SHAKE,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.TORCH_ON),
            id = "existing-1",
        )

        val importedRules = listOf(
            createSampleRule("imported-1", "Imported Rule A"),
            createSampleRule("imported-2", "Imported Rule B"),
        )

        val count = repository.importAutomations(importedRules, ImportStrategy.MERGE)
        assertThat(count).isEqualTo(2)

        val current = repository.automations.first()
        assertThat(current).hasSize(3)
        // Original rule still exists with original id
        assertThat(current.any { it.id == "existing-1" }).isTrue()
        // Imported rules exist with distinct generated IDs
        val importedA = current.find { it.name == "Imported Rule A" }
        assertThat(importedA).isNotNull()
        assertThat(importedA!!.id).isNotEqualTo("imported-1") // ID remapped
    }

    @Test
    fun importAutomations_replaceAllStrategy_overwritesAllRules() = runTest {
        // Initial rule in repo
        repository.add(
            name = "Old Existing Rule",
            triggerEvent = TriggerEvent.DEVICE_SHAKE,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.TORCH_ON),
            id = "old-1",
        )

        val importedRules = listOf(
            createSampleRule("new-1", "Brand New Rule"),
        )

        val count = repository.importAutomations(importedRules, ImportStrategy.REPLACE_ALL)
        assertThat(count).isEqualTo(1)

        val current = repository.automations.first()
        assertThat(current).hasSize(1)
        assertThat(current.first().name).isEqualTo("Brand New Rule")
        assertThat(current.none { it.name == "Old Existing Rule" }).isTrue()
    }

    @Test
    fun generateFileNames_producesSafeFormattedNames() {
        val backupName = BackupManager.generateBackupFileName()
        assertThat(backupName).startsWith("flowpilot_backup_")
        assertThat(backupName).endsWith(".json")

        val ruleName = BackupManager.generateRuleFileName("My Rule: Ultra / Power!")
        assertThat(ruleName).startsWith("flowpilot_My_Rule__Ultra___P")
        assertThat(ruleName).endsWith(".json")

        val encBackupName = BackupManager.generateEncryptedBackupFileName()
        assertThat(encBackupName).startsWith("flowpilot_encrypted_backup_")
        assertThat(encBackupName).endsWith(".json")

        val encRuleName = BackupManager.generateEncryptedRuleFileName("My Rule: Ultra / Power!")
        assertThat(encRuleName).startsWith("flowpilot_encrypted_My_Rule__Ultra___P")
        assertThat(encRuleName).endsWith(".json")
    }

    @Test
    fun exportEncryptedToString_fullSecretRoundtrip_preservesEnabledStateAndAllSecrets() {
        val rule1 = Automation(
            id = "rule-webhook",
            name = "Webhook Alert",
            enabled = true,
            triggerEvent = TriggerEvent.APP_OPENED,
            appPackage = "com.test.app",
            webhookUrl = "https://custom-webhook.example/endpoint?token=abc-123",
            webhookHeaders = "Authorization: Bearer secret-auth-token\nX-Header: custom",
            webhookBody = "{\"event\": \"triggered\", \"key\": 999}",
            phoneNumber = "+15550190001",
            smsSenderFilter = "+15550190002",
            smsRecipient = "+15550190003",
            smsMessage = "Verification PIN: 654321",
            wifiSsid = "Confidential-Office-Net",
            bluetoothDeviceAddress = "11:22:33:44:55:66",
            nfcTagId = "AABB0011",
            url = "https://example.com/target",
            createdAt = 1000L,
        )
        val rule2 = Automation(
            id = "rule-disabled",
            name = "Disabled Battery Rule",
            enabled = false,
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            batteryLevel = 10,
            actions = listOf(ActionType.BATTERY_SAVER_ON),
            createdAt = 2000L,
        )

        val password = "Strong#Password_2026"
        val encryptedJson = BackupManager.exportEncryptedToString(listOf(rule1, rule2), password)

        // Verify valid envelope structure
        assertThat(BackupManager.isEncryptedBackup(encryptedJson)).isTrue()
        assertThat(encryptedJson).contains("\"format\": \"flowpilot_encrypted_backup\"")
        assertThat(encryptedJson).contains("\"version\": 1")

        // Decrypt
        val restoreResult = BackupManager.parseEncryptedImport(encryptedJson, password)
        assertThat(restoreResult.isSuccess).isTrue()
        val restoredRules = restoreResult.getOrThrow()
        assertThat(restoredRules).hasSize(2)

        val restored1 = restoredRules[0]
        assertThat(restored1.id).isEqualTo("rule-webhook")
        assertThat(restored1.name).isEqualTo("Webhook Alert")
        assertThat(restored1.enabled).isTrue() // Enabled preserved
        assertThat(restored1.webhookUrl).isEqualTo("https://custom-webhook.example/endpoint?token=abc-123")
        assertThat(restored1.webhookHeaders).isEqualTo("Authorization: Bearer secret-auth-token\nX-Header: custom")
        assertThat(restored1.webhookBody).isEqualTo("{\"event\": \"triggered\", \"key\": 999}")
        assertThat(restored1.phoneNumber).isEqualTo("+15550190001")
        assertThat(restored1.smsSenderFilter).isEqualTo("+15550190002")
        assertThat(restored1.smsRecipient).isEqualTo("+15550190003")
        assertThat(restored1.smsMessage).isEqualTo("Verification PIN: 654321")
        assertThat(restored1.wifiSsid).isEqualTo("Confidential-Office-Net")
        assertThat(restored1.bluetoothDeviceAddress).isEqualTo("11:22:33:44:55:66")
        assertThat(restored1.nfcTagId).isEqualTo("AABB0011")

        val restored2 = restoredRules[1]
        assertThat(restored2.id).isEqualTo("rule-disabled")
        assertThat(restored2.enabled).isFalse() // Disabled preserved
    }

    @Test
    fun exportEncryptedToString_noPlaintextLeakageInCiphertextOrEnvelope() {
        val sensitiveRule = Automation(
            id = "secret-id-99",
            name = "Confidential Workflow",
            enabled = true,
            triggerEvent = TriggerEvent.APP_OPENED,
            webhookUrl = "https://leak-check.example/webhook-secret-url",
            webhookHeaders = "X-Secret-Auth: super_secret_token_12345",
            webhookBody = "leak_check_payload_xyz",
            phoneNumber = "+15550189999",
            smsSenderFilter = "+15550188888",
            smsRecipient = "+15550187777",
            smsMessage = "Super confidential SMS message 45678",
            wifiSsid = "Secret-Corporate-SSID",
            createdAt = 1234L,
        )

        val encryptedJson = BackupManager.exportEncryptedToString(listOf(sensitiveRule), "ValidPass123")

        assertThat(encryptedJson).doesNotContain("leak-check.example")
        assertThat(encryptedJson).doesNotContain("webhook-secret-url")
        assertThat(encryptedJson).doesNotContain("super_secret_token_12345")
        assertThat(encryptedJson).doesNotContain("leak_check_payload_xyz")
        assertThat(encryptedJson).doesNotContain("+15550189999")
        assertThat(encryptedJson).doesNotContain("+15550188888")
        assertThat(encryptedJson).doesNotContain("+15550187777")
        assertThat(encryptedJson).doesNotContain("Super confidential SMS message")
        assertThat(encryptedJson).doesNotContain("Secret-Corporate-SSID")
        assertThat(encryptedJson).doesNotContain("Confidential Workflow")
        assertThat(encryptedJson).doesNotContain("secret-id-99")
    }

    @Test
    fun parseEncryptedImport_wrongPassword_returnsSafeSecurityException() {
        val rule = createSampleRule("r-safe", "Safe Rule")
        val encryptedJson = BackupManager.exportEncryptedToString(listOf(rule), "CorrectPassword123")

        val result = BackupManager.parseEncryptedImport(encryptedJson, "WrongPassword456")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    }

    @Test
    fun parseEncryptedImport_tamperedData_returnsSafeFailure() {
        val rule = createSampleRule("r-tamper", "Tamper Test")
        val validJson = BackupManager.exportEncryptedToString(listOf(rule), "Password123")

        val envelope = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(FlowPilotEncryptedBackup.serializer(), validJson)

        // 1. Tamper ciphertext
        val tamperedCipher = if (envelope.ciphertext.startsWith("A")) "B" + envelope.ciphertext.drop(1) else "A" + envelope.ciphertext.drop(1)
        val tamperedCipherJson = kotlinx.serialization.json.Json.encodeToString(
            FlowPilotEncryptedBackup.serializer(),
            envelope.copy(ciphertext = tamperedCipher, data = tamperedCipher)
        )
        assertThat(BackupManager.parseEncryptedImport(tamperedCipherJson, "Password123").isFailure).isTrue()

        // 2. Tamper IV
        val tamperedIv = if (envelope.iv.startsWith("A")) "B" + envelope.iv.drop(1) else "A" + envelope.iv.drop(1)
        val tamperedIvJson = kotlinx.serialization.json.Json.encodeToString(
            FlowPilotEncryptedBackup.serializer(),
            envelope.copy(iv = tamperedIv)
        )
        assertThat(BackupManager.parseEncryptedImport(tamperedIvJson, "Password123").isFailure).isTrue()

        // 3. Tamper Salt
        val tamperedSalt = if (envelope.salt.startsWith("A")) "B" + envelope.salt.drop(1) else "A" + envelope.salt.drop(1)
        val tamperedSaltJson = kotlinx.serialization.json.Json.encodeToString(
            FlowPilotEncryptedBackup.serializer(),
            envelope.copy(salt = tamperedSalt)
        )
        assertThat(BackupManager.parseEncryptedImport(tamperedSaltJson, "Password123").isFailure).isTrue()
    }

    @Test
    fun parseEncryptedImport_rejectsUnsupportedVersionOrFormatOrIterations() {
        val rule = createSampleRule("r-meta", "Metadata Test")
        val validJson = BackupManager.exportEncryptedToString(listOf(rule), "Password123")
        val envelope = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(FlowPilotEncryptedBackup.serializer(), validJson)

        // Unsupported format
        val badFormatJson = kotlinx.serialization.json.Json.encodeToString(
            FlowPilotEncryptedBackup.serializer(),
            envelope.copy(format = "unsupported_foreign_format")
        )
        val badFormatResult = BackupManager.parseEncryptedImport(badFormatJson, "Password123")
        assertThat(badFormatResult.isFailure).isTrue()
        assertThat(badFormatResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(badFormatResult.exceptionOrNull()?.message).contains("Unsupported backup format")

        // Unsupported version
        val badVersionJson = kotlinx.serialization.json.Json.encodeToString(
            FlowPilotEncryptedBackup.serializer(),
            envelope.copy(version = 2)
        )
        val badVersionResult = BackupManager.parseEncryptedImport(badVersionJson, "Password123")
        assertThat(badVersionResult.isFailure).isTrue()
        assertThat(badVersionResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(badVersionResult.exceptionOrNull()?.message).contains("Unsupported backup version")

        // Invalid iterations
        val zeroIterationsJson = kotlinx.serialization.json.Json.encodeToString(
            FlowPilotEncryptedBackup.serializer(),
            envelope.copy(iterations = 0)
        )
        val zeroIterationsResult = BackupManager.parseEncryptedImport(zeroIterationsJson, "Password123")
        assertThat(zeroIterationsResult.isFailure).isTrue()
        assertThat(zeroIterationsResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(zeroIterationsResult.exceptionOrNull()?.message).contains("Iteration count out of allowed range")
    }

    @Test
    fun exportEncryptedSingleToString_roundtripSingleRule_preservesSecretsAndEnabledState() {
        val singleRule = Automation(
            id = "single-rule-1",
            name = "Single Encrypted Rule",
            enabled = true,
            triggerEvent = TriggerEvent.CHARGER_CONNECTED,
            webhookUrl = "https://api.single.example/hook",
            webhookHeaders = "X-Auth: secret-value",
            webhookBody = "{\"ping\": true}",
            createdAt = 500L,
        )

        val password = "SinglePassword!1"
        val encryptedJson = BackupManager.exportEncryptedSingleToString(singleRule, password)

        assertThat(BackupManager.isEncryptedBackup(encryptedJson)).isTrue()
        assertThat(encryptedJson).doesNotContain("https://api.single.example/hook")
        assertThat(encryptedJson).doesNotContain("secret-value")

        val result = BackupManager.parseEncryptedImport(encryptedJson, password)
        assertThat(result.isSuccess).isTrue()
        val rules = result.getOrThrow()
        assertThat(rules).hasSize(1)
        val restored = rules.first()
        assertThat(restored.id).isEqualTo("single-rule-1")
        assertThat(restored.name).isEqualTo("Single Encrypted Rule")
        assertThat(restored.enabled).isTrue()
        assertThat(restored.webhookUrl).isEqualTo("https://api.single.example/hook")
        assertThat(restored.webhookHeaders).isEqualTo("X-Auth: secret-value")
        assertThat(restored.webhookBody).isEqualTo("{\"ping\": true}")
    }

    @Test
    fun passwordValidation_enforcesSixCharacterMinimum_anyCharactersAllowed() {
        assertThat(BackupManager.isValidPassword("12345")).isFalse()
        assertThat(BackupManager.isValidPassword("")).isFalse()
        assertThat(BackupManager.isValidPassword(null as String?)).isFalse()

        assertThat(BackupManager.isValidPassword("123456")).isTrue()
        assertThat(BackupManager.isValidPassword("  a!  ")).isTrue()
        assertThat(BackupManager.isValidPassword("密碼1234")).isTrue()

        val rule = createSampleRule("r-pass", "Pass Rule")

        // Under 6 chars throws on export
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.exportEncryptedToString(listOf(rule), "12345")
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupManager.exportEncryptedSingleToString(rule, "12345")
        }

        // Under 6 chars fails on parse import
        val validJson = BackupManager.exportEncryptedToString(listOf(rule), "123456")
        val shortResult = BackupManager.parseEncryptedImport(validJson, "12345")
        assertThat(shortResult.isFailure).isTrue()
        assertThat(shortResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun regularExport_regressionPreserved_omitsSecretsAndForcesDisabled() {
        val sensitiveRule = Automation(
            id = "r-reg",
            name = "Regression Rule",
            enabled = true,
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            webhookUrl = "https://sensitive.example/reg",
            webhookHeaders = "Bearer secret",
            webhookBody = "secret body",
            createdAt = 100L,
        )

        // Regular export omits webhook secrets
        val regularFull = BackupManager.exportToString(listOf(sensitiveRule))
        assertThat(regularFull).doesNotContain("https://sensitive.example/reg")
        assertThat(regularFull).doesNotContain("secret body")

        // Regular import forces enabled = false
        val importedList = BackupManager.parseImport(regularFull).getOrThrow()
        assertThat(importedList.single().enabled).isFalse()

        // Regular import safely rejects encrypted backup
        val encryptedJson = BackupManager.exportEncryptedToString(listOf(sensitiveRule), "Password123")
        val rejectResult = BackupManager.parseImport(encryptedJson)
        assertThat(rejectResult.isFailure).isTrue()
        assertThat(rejectResult.exceptionOrNull()?.message).contains("Backup is encrypted")
    }

    @Test
    fun encryptedBackup_restoresAcrossDevices_reEncryptsWithLocalKeystore() = runTest {
        // Device 1: Rule has webhook secrets encrypted with Device 1's Keystore
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val device1Key = keyGen.generateKey()
        SecretCipher.secretKeyProvider = { device1Key }

        val originalRule = Automation(
            id = "cross-dev-rule",
            name = "Cross Device Rule",
            enabled = true,
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            webhookUrl = "https://cross-device.example/hook?key=xyz",
            webhookHeaders = "Authorization: Token dev1-token",
            webhookBody = "{\"cross_device\": true}",
            createdAt = 1000L,
        )
        // Store on Device 1 in repository (which calls withEncryptedSecrets())
        repository.replaceAll(listOf(originalRule))
        val storedRaw = repository.rawDataStore.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("rules")]!!
        assertThat(storedRaw).contains("enc:v1:")

        // Export encrypted backup from Device 1 (using rule from repository flow or model)
        val loadedFromDev1 = repository.automations.first()
        val backupJson = BackupManager.exportEncryptedToString(loadedFromDev1, "SharedBackupPass123")

        // Device 2: Reset SecretCipher key to Device 2's key (device 1's key is not accessible)
        val device2Key = keyGen.generateKey()
        SecretCipher.secretKeyProvider = { device2Key }

        // Decrypt backup on Device 2 using password only (standard PBKDF2/AES-GCM, no AndroidKeystore needed)
        val decryptedResult = BackupManager.parseEncryptedImport(backupJson, "SharedBackupPass123")
        assertThat(decryptedResult.isSuccess).isTrue()
        val importedRules = decryptedResult.getOrThrow()
        assertThat(importedRules).hasSize(1)
        val importedRule = importedRules.first()
        assertThat(importedRule.webhookUrl).isEqualTo("https://cross-device.example/hook?key=xyz")
        assertThat(importedRule.enabled).isTrue()

        // Import into Device 2 repository
        repository.rawDataStore.edit { it.clear() }
        repository.importAutomations(importedRules, ImportStrategy.REPLACE_ALL)

        // Verify Device 2 re-encrypted secrets with Device 2's key
        val dev2StoredRaw = repository.rawDataStore.data.first()[androidx.datastore.preferences.core.stringPreferencesKey("rules")]!!
        assertThat(dev2StoredRaw).contains("enc:v1:")
        assertThat(dev2StoredRaw).doesNotContain("https://cross-device.example")

        // Verify Device 2 runtime flow emits decrypted cleartext correctly
        val dev2Loaded = repository.automations.first().first()
        assertThat(dev2Loaded.webhookUrl).isEqualTo("https://cross-device.example/hook?key=xyz")
        assertThat(dev2Loaded.webhookHeaders).isEqualTo("Authorization: Token dev1-token")
        assertThat(dev2Loaded.webhookBody).isEqualTo("{\"cross_device\": true}")
        assertThat(dev2Loaded.enabled).isTrue()
    }

    @Test
    fun encryptedBackupFileNames_andPasswordValidation_followContracts() {
        val backupName = BackupManager.generateEncryptedBackupFileName()
        assertThat(backupName).startsWith("flowpilot_encrypted_backup_")
        assertThat(backupName).endsWith(".json")

        val ruleName = BackupManager.generateEncryptedRuleFileName("My Rule: Ultra / Power!")
        assertThat(ruleName).startsWith("flowpilot_encrypted_My_Rule__Ultra___P")
        assertThat(ruleName).endsWith(".json")

        assertThat(BackupManager.isValidPassword(null as CharSequence?)).isFalse()
        assertThat(BackupManager.isValidPassword("")).isFalse()
        assertThat(BackupManager.isValidPassword("12345")).isFalse()
        assertThat(BackupManager.isValidPassword("123456")).isTrue()
        assertThat(BackupManager.isValidPassword("secure-password-!@#$")).isTrue()
    }

    @Test
    fun parseEncryptedImport_rejectsOutOfRangeIterations_beforeDeriveKey() {
        val rule = createSampleRule("r-iter", "Iteration Test")
        val validJson = BackupManager.exportEncryptedToString(listOf(rule), "ValidPass123")

        // Reject iterations below MIN_PBKDF2_ITERATIONS (including zero and negative)
        val tooLowJson = validJson.replace(
            "\"iterations\": 100000",
            "\"iterations\": 9999"
        )
        val lowResult = BackupManager.parseEncryptedImport(tooLowJson, "ValidPass123")
        assertThat(lowResult.isFailure).isTrue()
        assertThat(lowResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(lowResult.exceptionOrNull()?.message).contains("Iteration count out of allowed range")

        val negativeJson = validJson.replace(
            "\"iterations\": 100000",
            "\"iterations\": -1"
        )
        val negResult = BackupManager.parseEncryptedImport(negativeJson, "ValidPass123")
        assertThat(negResult.isFailure).isTrue()
        assertThat(negResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(negResult.exceptionOrNull()?.message).contains("Iteration count out of allowed range")

        // Reject iterations above MAX_PBKDF2_ITERATIONS to prevent CPU DoS
        val tooHighJson = validJson.replace(
            "\"iterations\": 100000",
            "\"iterations\": 500001"
        )
        val highResult = BackupManager.parseEncryptedImport(tooHighJson, "ValidPass123")
        assertThat(highResult.isFailure).isTrue()
        assertThat(highResult.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(highResult.exceptionOrNull()?.message).contains("Iteration count out of allowed range")
    }

    @Test
    fun isEncryptedBackup_distinguishesPlainAndEncryptedEnvelopes() {
        val plainJson = BackupManager.exportToString(listOf(createSampleRule("r1", "Plain")))
        assertThat(BackupManager.isEncryptedBackup(plainJson)).isFalse()

        val encryptedJson = BackupManager.exportEncryptedToString(
            listOf(createSampleRule("r2", "Encrypted")),
            "secret123"
        )
        assertThat(BackupManager.isEncryptedBackup(encryptedJson)).isTrue()
        assertThat(BackupManager.isEncryptedBackup("not json")).isFalse()
    }
}
