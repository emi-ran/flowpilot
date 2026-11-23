package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowpilot.app.data.backup.BackupManager
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.security.SecretCipher
import com.flowpilot.app.data.security.SecretDecryptionException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

/** Silent data-loss guards: unreadable rules, duplicate IDs, and lost Keystore keys. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationRepositoryDataLossTest {

    private lateinit var context: Context
    private lateinit var repository: AutomationRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(Automation.serializer())
    private val rulesKey = stringPreferencesKey("rules")
    private val unreadableBackupKey = stringPreferencesKey("rules_backup_unreadable")
    private val duplicateBackupKey = stringPreferencesKey("rules_backup_duplicate_ids")

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        SecretCipher.secretKeyProvider = { key }
        repository = AutomationRepository(context)
    }

    @After
    fun tearDown() = runTest {
        repository.rawDataStore.edit { it.clear() }
        SecretCipher.secretKeyProvider = null
    }

    private fun rule(id: String, name: String = id, url: String = "") = Automation(
        id = id,
        name = name,
        triggerEvent = TriggerEvent.CHARGER_CONNECTED,
        action = ActionType.NFC_ON,
        actions = listOf(ActionType.NFC_ON),
        webhookUrl = url,
        createdAt = 1L,
    )

    private suspend fun rawRules() = repository.rawDataStore.data.first()[rulesKey]

    @Test
    fun corruptRules_writesAreRefused_rawKeptAndBackedUp_readsDoNotCrash() = runTest {
        val corrupt = "{definitely not a rules list"
        repository.rawDataStore.edit { it[rulesKey] = corrupt }

        assertThat(repository.automations.first()).isEmpty()

        val addError = runCatching {
            repository.add(
                name = "x",
                triggerEvent = TriggerEvent.CHARGER_CONNECTED,
                appPackage = "",
                appName = "",
                actions = listOf(ActionType.NFC_ON),
            )
        }.exceptionOrNull()
        assertThat(addError).isInstanceOf(AutomationRepository.CorruptRulesException::class.java)
        assertThat(addError!!.message).doesNotContain(corrupt)
        assertThat(runCatching { repository.delete("a") }.exceptionOrNull())
            .isInstanceOf(AutomationRepository.CorruptRulesException::class.java)
        assertThat(runCatching { repository.setEnabled("a", true) }.exceptionOrNull())
            .isInstanceOf(AutomationRepository.CorruptRulesException::class.java)

        // Engine paths fail closed without throwing.
        assertThat(repository.reserveExecution("a", 1L)).isNull()
        repository.patchLastTriggeredAt("a", 5L)

        assertThat(rawRules()).isEqualTo(corrupt)
        assertThat(repository.rawDataStore.data.first()[unreadableBackupKey]).isEqualTo(corrupt)
    }

    @Test
    fun unknownEnumValue_isTreatedAsUnreadable_andNeverOverwritten() = runTest {
        val raw = "[{\"id\":\"a\",\"name\":\"a\",\"triggerEvent\":\"NOT_A_REAL_TRIGGER\",\"createdAt\":1}]"
        repository.rawDataStore.edit { it[rulesKey] = raw }

        assertThat(runCatching { repository.deleteMany(setOf("a")) }.exceptionOrNull())
            .isInstanceOf(AutomationRepository.CorruptRulesException::class.java)
        assertThat(rawRules()).isEqualTo(raw)
    }

    @Test
    fun replaceAll_overUnreadableRules_backsUpRawFirst() = runTest {
        val corrupt = "[{\"id\":\"a\",\"triggerEvent\":\"NOT_A_REAL_TRIGGER\"}]"
        repository.rawDataStore.edit { it[rulesKey] = corrupt }

        repository.replaceAll(listOf(rule("fresh")))

        assertThat(repository.rawDataStore.data.first()[unreadableBackupKey]).isEqualTo(corrupt)
        assertThat(repository.automations.first().map { it.id }).containsExactly("fresh")
    }

    @Test
    fun duplicateIds_failClosedToFirst_andRawIsBackedUpBeforeWrite() = runTest {
        val raw = json.encodeToString(
            listSerializer,
            listOf(rule("dup", "first"), rule("dup", "second"), rule("other")),
        )
        repository.rawDataStore.edit { it[rulesKey] = raw }

        val first = repository.automations.first()
        val second = repository.automations.first()
        assertThat(first.map { it.name }).containsExactly("first", "other").inOrder()
        assertThat(first.map { it.id }).containsExactly("dup", "other").inOrder()
        assertThat(first.map { it.id }).isEqualTo(second.map { it.id })

        repository.setEnabled("other", true)

        assertThat(repository.rawDataStore.data.first()[duplicateBackupKey]).isEqualTo(raw)
        assertThat(repository.automations.first().map { it.name })
            .containsExactly("first", "other")
    }

    @Test
    fun lostKeystoreKey_doesNotErasePersistedSecret_andBlocksExportAndClone() = runTest {
        repository.replaceAll(listOf(rule("hook", url = "https://example.com/secret-hook")))
        val storedCiphertext = json.decodeFromString(listSerializer, rawRules()!!).single().webhookUrl
        assertThat(storedCiphertext).startsWith("enc:v1:")

        val lostKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        SecretCipher.secretKeyProvider = { lostKey }

        val loaded = repository.automations.first().single()
        assertThat(loaded.webhookUrl).isEqualTo(storedCiphertext)

        assertThat(
            runCatching {
                BackupManager.exportEncryptedToString(listOf(loaded), "correct horse battery staple")
            }.exceptionOrNull()
        ).isInstanceOf(SecretDecryptionException::class.java)
        assertThat(runCatching { repository.duplicate("hook", "copy") }.exceptionOrNull())
            .isInstanceOf(SecretDecryptionException::class.java)

        repository.update(loaded.copy(name = "renamed"))

        val after = json.decodeFromString(listSerializer, rawRules()!!).single()
        assertThat(after.name).isEqualTo("renamed")
        assertThat(after.webhookUrl).isEqualTo(storedCiphertext)
    }
}
