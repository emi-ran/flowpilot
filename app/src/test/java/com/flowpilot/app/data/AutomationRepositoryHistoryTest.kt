package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.data.model.ExecutionStatus
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationRepositoryHistoryTest {

    private lateinit var context: Context
    private lateinit var repository: AutomationRepository

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val historySerializer = ListSerializer(ExecutionHistoryEntry.serializer())
    private val rulesSerializer = ListSerializer(Automation.serializer())
    private val historyKey = stringPreferencesKey("execution_history")
    private val rulesKey = stringPreferencesKey("rules")

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        repository = AutomationRepository(context)
    }

    @After
    fun tearDown() = runTest {
        repository.rawDataStore.edit { it.clear() }
    }

    @Test
    fun appendHistory_prependsNewestEntry_andRetainsMax100() = runTest {
        for (i in 1..105) {
            val entry = ExecutionHistoryEntry.create(
                id = "entry-$i",
                ruleId = "rule-1",
                ruleName = "Rule 1",
                trigger = "MANUAL",
                timestamp = 1000L + i,
                actions = listOf(
                    ActionExecutionRecord.create(ActionType.NFC_ON, true, "NFC enabled")
                ),
            )
            repository.appendHistory(entry)
        }

        val history = repository.executionHistory.first()
        assertThat(history).hasSize(100)
        // Newest must be first
        assertThat(history.first().id).isEqualTo("entry-105")
        assertThat(history.first().timestamp).isEqualTo(1105L)
        // Oldest retained must be entry-6 (105 down to 6 = 100 items)
        assertThat(history.last().id).isEqualTo("entry-6")
        assertThat(history.last().timestamp).isEqualTo(1006L)
    }

    @Test
    fun legacySmsRule_historyName_isNormalized() {
        val rule = Automation(
            id = "legacy",
            name = "SMS from 555-0100 · NFC enabled",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            actions = listOf(ActionType.NFC_ON),
            createdAt = 1L,
        )

        assertThat(rule.normalizedName).isEqualTo("SMS Received · Turn NFC on")
    }

    @Test
    fun customSmsName_resemblingLegacyFormat_isPreserved() {
        val rule = Automation(
            id = "custom",
            name = "SMS from Alice · NFC enabled",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            actions = listOf(ActionType.NFC_ON),
            createdAt = 1L,
        )

        assertThat(rule.normalizedName).isEqualTo(rule.name)
    }

    @Test
    fun smsGeneratedName_requiresThreeDigits_andPreservesPlusOnlySender() {
        val base = Automation(
            id = "edge",
            name = "",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            actions = listOf(ActionType.NFC_ON),
            createdAt = 1L,
        )

        assertThat(base.copy(name = "SMS from + · NFC enabled").normalizedName)
            .isEqualTo("SMS from + · NFC enabled")
        assertThat(base.copy(name = "SMS from 1 · NFC enabled").normalizedName)
            .isEqualTo("SMS from 1 · NFC enabled")
        assertThat(base.copy(name = "SMS from 12 · NFC enabled").normalizedName)
            .isEqualTo("SMS from 12 · NFC enabled")
        assertThat(base.copy(name = "SMS from +1 (555) 010-0 · NFC enabled").normalizedName)
            .isEqualTo("SMS Received · Turn NFC on")
    }

    @Test
    fun executionHistory_normalizesLegacySmsNames_preservesCustomNames() = runTest {
        val legacySmsRule = Automation(
            id = "legacy",
            name = "SMS from 555-0100 · NFC enabled",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            actions = listOf(ActionType.NFC_ON),
            createdAt = 1L,
        )
        val customSmsRule = legacySmsRule.copy(
            id = "custom",
            name = "SMS from Alice · NFC enabled",
        )
        val entries = listOf(
            ExecutionHistoryEntry.create(
                id = "legacy-entry",
                ruleId = "legacy",
                ruleName = legacySmsRule.name,
                trigger = TriggerEvent.SMS_RECEIVED.name,
                timestamp = 1L,
                actions = listOf(ActionExecutionRecord.create(ActionType.NFC_ON, true, "done")),
            ),
            ExecutionHistoryEntry.create(
                id = "custom-entry",
                ruleId = "custom",
                ruleName = customSmsRule.name,
                trigger = TriggerEvent.SMS_RECEIVED.name,
                timestamp = 2L,
                actions = listOf(ActionExecutionRecord.create(ActionType.NFC_ON, true, "done")),
            ),
        )
        repository.rawDataStore.edit { prefs ->
            prefs[rulesKey] = json.encodeToString(rulesSerializer, listOf(legacySmsRule, customSmsRule))
            prefs[historyKey] = json.encodeToString(historySerializer, entries)
        }

        val history = repository.executionHistory.first()

        assertThat(history.map { it.ruleName }).containsExactly(
            "SMS Received · NFC enabled",
            customSmsRule.name,
        ).inOrder()
    }

    private fun smsSnapshot(name: String = "SMS from 555-0100 · NFC enabled") =
        ExecutionHistoryEntry.create(
            id = "snapshot", ruleId = "legacy", ruleName = name,
            trigger = TriggerEvent.SMS_RECEIVED.name, timestamp = 1L, actions = emptyList(),
        )

    private suspend fun seedLegacyHistory(): Automation {
        val rule = Automation(
            id = "legacy", name = "Current custom name",
            triggerEvent = TriggerEvent.SMS_RECEIVED,
            actions = listOf(ActionType.VIBRATE), createdAt = 1L,
        )
        repository.rawDataStore.edit { prefs ->
            prefs[rulesKey] = json.encodeToString(rulesSerializer, listOf(rule))
            prefs[historyKey] = json.encodeToString(historySerializer, listOf(
                smsSnapshot(), smsSnapshot("Historical custom name").copy(id = "custom"),
            ))
        }
        return rule
    }

    private suspend fun assertPersistedSnapshots() {
        val raw = repository.rawDataStore.data.first()[historyKey]!!
        assertThat(raw).doesNotContain("555-0100")
        assertThat(json.decodeFromString(historySerializer, raw).map { it.ruleName })
            .containsExactly("SMS Received · NFC enabled", "Historical custom name").inOrder()
        assertThat(repository.executionHistory.first().map { it.ruleName })
            .containsExactly("SMS Received · NFC enabled", "Historical custom name").inOrder()
    }

    @Test
    fun historyRead_persistsMigration_preservesHistoricalNamesAndSummary() = runTest {
        seedLegacyHistory()
        repository.executionHistory.first()
        assertPersistedSnapshots()
        repository.delete("legacy")
        assertPersistedSnapshots()
    }

    @Test
    fun deleteBeforeHistoryRead_persistsMigration() = runTest {
        seedLegacyHistory()
        repository.delete("legacy")
        assertPersistedSnapshots()
    }

    @Test
    fun deleteManyBeforeHistoryRead_persistsMigration() = runTest {
        seedLegacyHistory()
        repository.deleteMany(setOf("legacy"))
        assertPersistedSnapshots()
    }

    @Test
    fun renameAndTriggerChangeBeforeHistoryRead_preserveSnapshots() = runTest {
        val rule = seedLegacyHistory()
        repository.update(rule.copy(name = "Renamed", triggerEvent = TriggerEvent.CHARGER_CONNECTED))
        assertPersistedSnapshots()
    }

    @Test
    fun replaceImportBeforeHistoryRead_preservesSnapshots() = runTest {
        val rule = seedLegacyHistory()
        repository.importAutomations(
            listOf(rule.copy(name = "Imported", triggerEvent = TriggerEvent.CHARGER_CONNECTED)),
            com.flowpilot.app.data.backup.ImportStrategy.REPLACE_ALL,
        )
        assertPersistedSnapshots()
    }

    @Test
    fun replaceAllBeforeHistoryRead_persistsMigration() = runTest {
        seedLegacyHistory()
        repository.replaceAll(emptyList())
        assertPersistedSnapshots()
    }

    @Test
    fun orphanedAndManualSnapshots_migrateWithoutCurrentRule() = runTest {
        repository.rawDataStore.edit { prefs ->
            prefs[historyKey] = json.encodeToString(historySerializer, listOf(
                smsSnapshot(), smsSnapshot().copy(id = "manual", trigger = "MANUAL"),
            ))
        }
        assertThat(repository.executionHistory.first().map { it.ruleName })
            .containsExactly("SMS Received · NFC enabled", "SMS Received · NFC enabled")
        assertThat(repository.rawDataStore.data.first()[historyKey]).doesNotContain("555-0100")
    }

    @Test
    fun appendHistory_normalizesBeforePersistence() = runTest {
        repository.appendHistory(smsSnapshot())
        assertThat(repository.rawDataStore.data.first()[historyKey]).doesNotContain("555-0100")
    }

    @Test
    fun nonSmsSnapshotAndCustomSmsNames_stayUnchanged() {
        for (name in listOf("SMS from Alice · NFC enabled", "SMS from 12 · NFC enabled", "My SMS rule")) {
            assertThat(smsSnapshot(name).normalizedRuleName).isEqualTo(name)
        }
        val nonSms = smsSnapshot().copy(trigger = TriggerEvent.CHARGER_CONNECTED.name)
        assertThat(nonSms.normalizedRuleName).isEqualTo(nonSms.ruleName)
    }

    @Test
    fun clearHistory_removesAllEntries() = runTest {
        val entry = ExecutionHistoryEntry.create(
            id = "entry-1",
            ruleId = "rule-1",
            ruleName = "Rule 1",
            trigger = "MANUAL",
            timestamp = 1000L,
            actions = listOf(
                ActionExecutionRecord.create(ActionType.NFC_ON, true, "NFC enabled")
            ),
        )
        repository.appendHistory(entry)
        assertThat(repository.executionHistory.first()).hasSize(1)

        repository.clearHistory()
        assertThat(repository.executionHistory.first()).isEmpty()
    }

    @Test
    fun safeDecodeHistory_onCorruptedData_failsClosedToEmptyWithoutAffectingAutomations() = runTest {
        repository.rawDataStore.edit { prefs ->
            prefs[historyKey] = "{ invalid json array"
        }

        val history = repository.executionHistory.first()
        assertThat(history).isEmpty()
    }

    @Test
    fun executionStatus_computesCorrectly() {
        val success = ExecutionStatus.fromCounts(2, 0)
        assertThat(success).isEqualTo(ExecutionStatus.SUCCESS)

        val partial = ExecutionStatus.fromCounts(1, 1)
        assertThat(partial).isEqualTo(ExecutionStatus.PARTIAL)

        val failure = ExecutionStatus.fromCounts(0, 2)
        assertThat(failure).isEqualTo(ExecutionStatus.FAILURE)

        val emptyFailure = ExecutionStatus.fromCounts(0, 0)
        assertThat(emptyFailure).isEqualTo(ExecutionStatus.FAILURE)
    }

    @Test
    fun actionExecutionRecord_redactsSensitiveInformation() {
        val rawMessage = "Failed contacting https://admin:password123@example.com/api?token=sec_abc123 with Bearer my_secret_token"
        val record = ActionExecutionRecord.create(
            actionType = ActionType.HTTP_WEBHOOK,
            success = false,
            message = rawMessage,
        )

        assertThat(record.message).doesNotContain("password123")
        assertThat(record.message).doesNotContain("sec_abc123")
        assertThat(record.message).doesNotContain("my_secret_token")
        assertThat(record.message).contains("[REDACTED]")
    }
}
