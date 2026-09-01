package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.ExecutionHistoryEntry
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
    private val historyKey = stringPreferencesKey("execution_history")

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
