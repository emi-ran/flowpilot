package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.security.SecretCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationRepositoryCryptoTest {

    private lateinit var context: Context
    private lateinit var repository: AutomationRepository
    private lateinit var testSecretKey: SecretKey

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listSerializer = ListSerializer(Automation.serializer())
    private val key = stringPreferencesKey("rules")

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

    @Test
    fun add_smsRule_withSenderFilter_usesGenericGeneratedName() = runTest {
        val rule = repository.add(
            name = "",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.NFC_ON),
            smsSenderFilter = "555-0100",
        )

        assertThat(rule.name).isEqualTo("SMS Received · Turn NFC on")
        assertThat(rule.name).doesNotContain("555-0100")
    }

    @Test
    fun automations_legacySmsGeneratedName_isNormalizedWithoutSender() = runTest {
        val legacy = Automation(
            id = "legacy",
            name = "SMS from 555-0100 · NFC enabled",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            actions = listOf(ActionType.NFC_ON),
            createdAt = 1L,
        )
        repository.rawDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(listSerializer, listOf(legacy))
        }

        val loaded = repository.automations.first().single()

        assertThat(loaded.name).isEqualTo("SMS Received · Turn NFC on")
    }

    @Test
    fun update_legacySmsGeneratedName_persistsNormalizedName() = runTest {
        val legacy = Automation(
            id = "legacy",
            name = "SMS from 555-0100 · NFC enabled",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            actions = listOf(ActionType.NFC_ON),
            createdAt = 1L,
        )
        repository.rawDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(listSerializer, listOf(legacy))
        }

        repository.update(legacy)

        assertThat(repository.automations.first().single().name)
            .isEqualTo("SMS Received · Turn NFC on")
    }

    @Test
    fun customSmsName_isPreserved() = runTest {
        val rule = repository.add(
            name = "Family alerts",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.NFC_ON),
        )

        assertThat(repository.automations.first().single().name).isEqualTo(rule.name)
        assertThat(rule.name).isEqualTo("Family alerts")
    }

    @Test
    fun importAutomations_normalizesLegacySmsNamesBeforePersisting() = runTest {
        val legacy = Automation("legacy", "SMS from 555-0100 · NFC enabled", triggerEvent = TriggerEvent.SMS_RECEIVED, actions = listOf(ActionType.NFC_ON), createdAt = 1L)
        val custom = legacy.copy(id = "custom", name = "SMS from Alice · NFC enabled")

        repository.importAutomations(listOf(legacy, custom), com.flowpilot.app.data.backup.ImportStrategy.REPLACE_ALL)

        val stored = json.decodeFromString(listSerializer, repository.rawDataStore.data.first()[key]!!)
        assertThat(stored.map { it.name }).containsExactly("SMS Received · Turn NFC on", custom.name).inOrder()
    }

    @Test
    fun replaceAll_normalizesLegacySmsNamesBeforePersisting() = runTest {
        val legacy = Automation("legacy", "SMS from 555-0100 · NFC enabled", triggerEvent = TriggerEvent.SMS_RECEIVED, actions = listOf(ActionType.NFC_ON), createdAt = 1L)

        repository.replaceAll(listOf(legacy))

        val stored = json.decodeFromString(listSerializer, repository.rawDataStore.data.first()[key]!!)
        assertThat(stored.single().name).isEqualTo("SMS Received · Turn NFC on")
    }

    @Test
    fun add_webhookRule_persistsEncryptedInStore_andEmitsDecryptedInFlow() = runTest {
        val rule = repository.add(
            name = "Webhook Alert",
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.HTTP_WEBHOOK),
            webhookUrl = "https://user:secret@example.com/api?token=sec123",
            webhookHeaders = "Authorization: Bearer secret_token_xyz\nX-Api-Key: key_123",
            webhookBody = "{\"secret\": \"payload\"}",
        )

        // Verify emitted Flow decrypts transparently
        val loadedList = repository.automations.first()
        assertThat(loadedList).hasSize(1)
        val loaded = loadedList.first()
        assertThat(loaded.id).isEqualTo(rule.id)
        assertThat(loaded.webhookUrl).isEqualTo("https://user:secret@example.com/api?token=sec123")
        assertThat(loaded.webhookHeaders).isEqualTo("Authorization: Bearer secret_token_xyz\nX-Api-Key: key_123")
        assertThat(loaded.webhookBody).isEqualTo("{\"secret\": \"payload\"}")

        // Inspect raw DataStore content to ensure secrets are encrypted at rest
        val prefs = repository.rawDataStore.data.first()
        val rawJson = prefs[key] ?: ""
        val storedList = json.decodeFromString(listSerializer, rawJson)
        val stored = storedList.first()

        assertThat(stored.webhookUrl).startsWith("enc:v1:")
        assertThat(stored.webhookHeaders).startsWith("enc:v1:")
        assertThat(stored.webhookBody).startsWith("enc:v1:")
        assertThat(stored.webhookUrl).doesNotContain("secret")
        assertThat(stored.webhookHeaders).doesNotContain("secret_token_xyz")
        assertThat(stored.webhookBody).doesNotContain("payload")
    }

    @Test
    fun update_webhookRule_persistsEncrypted() = runTest {
        val rule = repository.add(
            name = "Initial",
            triggerEvent = TriggerEvent.CHARGER_CONNECTED,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.HTTP_WEBHOOK),
            webhookUrl = "https://example.com/initial",
        )

        val modified = rule.copy(
            webhookUrl = "https://user:newpassword@example.com/v2",
            webhookHeaders = "Authorization: Bearer new_token",
            webhookBody = "{\"updated\": true}",
        )
        repository.update(modified)

        val loaded = repository.automations.first().first()
        assertThat(loaded.webhookUrl).isEqualTo("https://user:newpassword@example.com/v2")
        assertThat(loaded.webhookHeaders).isEqualTo("Authorization: Bearer new_token")
        assertThat(loaded.webhookBody).isEqualTo("{\"updated\": true}")

        val rawJson = repository.rawDataStore.data.first()[key] ?: ""
        val stored = json.decodeFromString(listSerializer, rawJson).first()
        assertThat(stored.webhookUrl).startsWith("enc:v1:")
        assertThat(stored.webhookUrl).doesNotContain("newpassword")
        assertThat(stored.webhookHeaders).startsWith("enc:v1:")
        assertThat(stored.webhookHeaders).doesNotContain("new_token")
    }

    @Test
    fun mutateExistingRule_patchLastTriggered_setEnabled_migratesPlaintextSecrets() = runTest {
        // Seed legacy unencrypted rule directly into DataStore
        val legacyRule = Automation(
            id = "legacy-rule-1",
            name = "Legacy",
            triggerEvent = TriggerEvent.APP_OPENED,
            action = ActionType.HTTP_WEBHOOK,
            actions = listOf(ActionType.HTTP_WEBHOOK),
            webhookUrl = "https://user:plain@example.com/webhook",
            webhookHeaders = "Authorization: Bearer plain_token",
            webhookBody = "{\"plain\": true}",
            createdAt = 5000L,
            lastTriggeredAt = 0L,
            enabled = true,
        )
        repository.rawDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(listSerializer, listOf(legacyRule))
        }

        // Action: patchLastTriggeredAt
        repository.patchLastTriggeredAt("legacy-rule-1", 9999L)

        // Verify DataStore storage is now encrypted
        val rawJson1 = repository.rawDataStore.data.first()[key] ?: ""
        val stored1 = json.decodeFromString(listSerializer, rawJson1).first()
        assertThat(stored1.lastTriggeredAt).isEqualTo(9999L)
        assertThat(stored1.webhookUrl).startsWith("enc:v1:")
        assertThat(stored1.webhookHeaders).startsWith("enc:v1:")
        assertThat(stored1.webhookBody).startsWith("enc:v1:")
        assertThat(stored1.webhookUrl).doesNotContain("plain")

        // Action: setEnabled
        repository.setEnabled("legacy-rule-1", false)

        val rawJson2 = repository.rawDataStore.data.first()[key] ?: ""
        val stored2 = json.decodeFromString(listSerializer, rawJson2).first()
        assertThat(stored2.enabled).isFalse()
        assertThat(stored2.webhookUrl).startsWith("enc:v1:")
        assertThat(stored2.webhookHeaders).startsWith("enc:v1:")
        assertThat(stored2.webhookBody).startsWith("enc:v1:")

        // Verify runtime reads back clear text
        val loaded = repository.automations.first().first()
        assertThat(loaded.enabled).isFalse()
        assertThat(loaded.lastTriggeredAt).isEqualTo(9999L)
        assertThat(loaded.webhookUrl).isEqualTo("https://user:plain@example.com/webhook")
        assertThat(loaded.webhookHeaders).isEqualTo("Authorization: Bearer plain_token")
        assertThat(loaded.webhookBody).isEqualTo("{\"plain\": true}")
    }

    @Test
    fun migrateLegacySecretsIfNeeded_encryptsPlaintextAtomicallyWithoutLoops() = runTest {
        val legacy1 = Automation(
            id = "leg-1",
            name = "Legacy 1",
            triggerEvent = TriggerEvent.BATTERY_ABOVE,
            action = ActionType.HTTP_WEBHOOK,
            actions = listOf(ActionType.HTTP_WEBHOOK),
            webhookUrl = "https://legacy1.com",
            webhookHeaders = "Auth: 123",
            webhookBody = "b1",
            createdAt = 100L,
        )
        val legacy2 = Automation(
            id = "leg-2",
            name = "Legacy 2",
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            action = ActionType.HTTP_WEBHOOK,
            actions = listOf(ActionType.HTTP_WEBHOOK),
            webhookUrl = "https://legacy2.com",
            createdAt = 200L,
        )
        repository.rawDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(listSerializer, listOf(legacy1, legacy2))
        }

        // Run explicit startup migration
        repository.migrateLegacySecretsIfNeeded()

        val rawJson = repository.rawDataStore.data.first()[key] ?: ""
        val stored = json.decodeFromString(listSerializer, rawJson)
        assertThat(stored[0].webhookUrl).startsWith("enc:v1:")
        assertThat(stored[0].webhookHeaders).startsWith("enc:v1:")
        assertThat(stored[0].webhookBody).startsWith("enc:v1:")
        assertThat(stored[1].webhookUrl).startsWith("enc:v1:")

        // Subsequent migration run is idempotent no-op
        repository.migrateLegacySecretsIfNeeded()
        val rawJsonAfter = repository.rawDataStore.data.first()[key] ?: ""
        assertThat(rawJsonAfter).isEqualTo(rawJson)
    }

    @Test
    fun deleteAndBulkDelete_encryptsRemainingLegacyPlaintext() = runTest {
        val legacy1 = Automation(
            id = "del-1",
            name = "Delete Me",
            triggerEvent = TriggerEvent.APP_OPENED,
            action = ActionType.NFC_ON,
            createdAt = 100L,
        )
        val legacy2 = Automation(
            id = "keep-2",
            name = "Keep Me",
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            action = ActionType.HTTP_WEBHOOK,
            actions = listOf(ActionType.HTTP_WEBHOOK),
            webhookUrl = "https://keep.com/secret",
            createdAt = 200L,
        )
        repository.rawDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(listSerializer, listOf(legacy1, legacy2))
        }

        repository.delete("del-1")

        val rawJson = repository.rawDataStore.data.first()[key] ?: ""
        val stored = json.decodeFromString(listSerializer, rawJson)
        assertThat(stored).hasSize(1)
        assertThat(stored.first().id).isEqualTo("keep-2")
        assertThat(stored.first().webhookUrl).startsWith("enc:v1:")
        assertThat(stored.first().webhookUrl).doesNotContain("secret")
    }
}
