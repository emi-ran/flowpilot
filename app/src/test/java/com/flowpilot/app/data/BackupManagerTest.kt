package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.flowpilot.app.data.backup.BackupManager
import com.flowpilot.app.data.backup.FlowPilotBackup
import com.flowpilot.app.data.backup.ImportStrategy
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var repository: AutomationRepository

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        repository = AutomationRepository(context)
    }

    @After
    fun tearDown() = runTest {
        repository.rawDataStore.edit { it.clear() }
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
    }
}
