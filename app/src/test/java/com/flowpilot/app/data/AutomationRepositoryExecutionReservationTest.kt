package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.ActionExecutionRecord
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.actions.ActionResultCode
import com.flowpilot.app.data.security.SecretCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import javax.crypto.SecretKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationRepositoryExecutionReservationTest {
    private lateinit var context: Context
    private lateinit var repository: AutomationRepository
    private lateinit var testSecretKey: SecretKey
    private val rulesKey = stringPreferencesKey("rules")
    private val rulesSerializer = ListSerializer(Automation.serializer())
    private val json = Json { encodeDefaults = true }

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        testSecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        SecretCipher.secretKeyProvider = { testSecretKey }
        repository = AutomationRepository(context)
    }

    @After
    fun tearDown() = runTest {
        repository.rawDataStore.edit { it.clear() }
        SecretCipher.secretKeyProvider = null
    }

    @Test
    fun reserveExecution_claimsCooldownAtomically() = runTest {
        val rule = addRule(cooldownMinutes = 5)
        val snapshot = repository.automations.first().single()
        val gate = CompletableDeferred<Unit>()

        val reservations = (1..2).map {
            async(Dispatchers.IO) {
                gate.await()
                repository.reserveExecution(snapshot.id, snapshot.executionRevision, at = 10_000L)
            }
        }
        gate.complete(Unit)

        assertThat(reservations.awaitAll().filterNotNull()).hasSize(1)
        assertThat(repository.automations.first().single().lastTriggeredAt).isEqualTo(10_000L)
        assertThat(repository.executionHistory.first()).isEmpty()
    }

    @Test
    fun reserveExecution_zeroCooldownStillDeniesConcurrentLease_andReleaseRequiresOwnerToken() = runTest {
        val rule = addRule(cooldownMinutes = 0)
        val snapshot = repository.automations.first().single()
        val first = requireNotNull(repository.reserveExecution(snapshot.id, snapshot.executionRevision, at = 10_000L))

        assertThat(repository.reserveExecution(snapshot.id, snapshot.executionRevision, at = 10_001L)).isNull()
        repository.releaseExecutionReservation(first.copy(token = "wrong"), successful = false)
        assertThat(repository.reserveExecution(snapshot.id, snapshot.executionRevision, at = 10_002L)).isNull()
        repository.releaseExecutionReservation(first, successful = false)
        assertThat(repository.reserveExecution(snapshot.id, snapshot.executionRevision, at = 10_003L)).isNotNull()
    }

    @Test
    fun cancelledReservation_restoresPriorCooldown_andPersistsCancelledHistory() = runTest {
        val rule = addRule(cooldownMinutes = 5)
        repository.patchLastTriggeredAt(rule.id, 1_000L)
        val snapshot = repository.automations.first().single()
        val reservation = requireNotNull(repository.reserveExecution(snapshot.id, snapshot.executionRevision, at = 400_000L))

        repository.releaseExecutionReservation(reservation, successful = false)
        repository.appendHistory(
            ExecutionHistoryEntry.create(
                ruleId = rule.id,
                ruleName = rule.name,
                trigger = rule.triggerEvent.name,
                actions = listOf(
                    ActionExecutionRecord.create(
                        actionType = ActionType.SHOW_NOTIFICATION,
                        success = false,
                        message = "Execution cancelled",
                        resultCode = ActionResultCode.EXECUTION_CANCELLED,
                    )
                ),
            )
        )

        assertThat(repository.automations.first().single().lastTriggeredAt).isEqualTo(1_000L)
        assertThat(repository.executionHistory.first().single().actions.single().resultCode)
            .isEqualTo(ActionResultCode.EXECUTION_CANCELLED)
    }

    @Test
    fun executionAuthorization_revokesDisabledDeletedAndChangedRules() = runTest {
        val disabled = addRule(id = "disabled")
        val disabledRevision = reserve(disabled)
        repository.setEnabled(disabled.id, false)
        assertThat(repository.isExecutionAuthorized(disabled.id, disabledRevision)).isFalse()

        val changed = addRule(id = "changed")
        val changedRevision = reserve(changed)
        repository.update(changed.copy(notificationTitle = "Changed"))
        assertThat(repository.isExecutionAuthorized(changed.id, changedRevision)).isFalse()
        assertThat(repository.reserveExecution(changed.id, changedRevision, at = 20_000L)).isNull()

        val deleted = addRule(id = "deleted")
        val deletedRevision = reserve(deleted)
        repository.delete(deleted.id)
        assertThat(repository.isExecutionAuthorized(deleted.id, deletedRevision)).isFalse()
    }

    @Test
    fun dispatchAuthorization_serializesMutationAndRejectsRevokedLease() = runTest {
        val rule = addRule()
        val snapshot = repository.automations.first().single()
        val reservation = requireNotNull(repository.reserveExecution(snapshot.id, snapshot.executionRevision))

        repository.setEnabled(rule.id, false)

        assertThat(repository.dispatchIfAuthorized(reservation) { "side effect" }).isNull()
    }

    @Test
    fun replaceAll_revokesSameIdReservationAndKeepsWebhookSecretEncrypted() = runTest {
        val rule = repository.add(
            name = "Webhook",
            triggerEvent = TriggerEvent.CHARGER_CONNECTED,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.HTTP_WEBHOOK),
            webhookUrl = "https://example.test/hook?token=secret",
            id = "replacement",
        )
        val revision = reserve(rule)
        val storedAfterReservation = json.decodeFromString(
            rulesSerializer,
            repository.rawDataStore.data.first()[rulesKey]!!,
        ).single()
        assertThat(storedAfterReservation.webhookUrl).startsWith("enc:v1:")
        assertThat(storedAfterReservation.webhookUrl).doesNotContain("secret")

        repository.replaceAll(listOf(rule.copy(notificationTitle = "Replacement")))

        assertThat(repository.isExecutionAuthorized(rule.id, revision)).isFalse()
        val stored = json.decodeFromString(rulesSerializer, repository.rawDataStore.data.first()[rulesKey]!!).single()
        assertThat(stored.executionRevision).isGreaterThan(revision)
        assertThat(stored.webhookUrl).startsWith("enc:v1:")
        assertThat(stored.webhookUrl).doesNotContain("secret")
    }

    @Test
    fun persistenceRejectsDuplicateIds_andLegacyDuplicateReadsFailClosedToFirstRule() = runTest {
        val rule = addRule(id = "duplicate")
        val duplicate = rule.copy(name = "Second")
        repository.rawDataStore.edit { prefs ->
            prefs[rulesKey] = json.encodeToString(rulesSerializer, listOf(rule, duplicate))
        }

        assertThat(repository.automations.first()).containsExactly(rule)
        assertThat(runCatching { repository.replaceAll(listOf(rule, duplicate)) }.isFailure).isTrue()
        assertThat(runCatching { addRule(id = "duplicate") }.isFailure).isTrue()
    }

    private suspend fun reserve(rule: Automation): Long {
        val snapshot = repository.automations.first().first { it.id == rule.id }
        assertThat(repository.reserveExecution(snapshot.id, snapshot.executionRevision, at = 10_000L)).isNotNull()
        return snapshot.executionRevision
    }

    private suspend fun addRule(id: String = "rule", cooldownMinutes: Int = 0): Automation = repository.add(
        name = "Rule $id",
        triggerEvent = TriggerEvent.CHARGER_CONNECTED,
        appPackage = "",
        appName = "",
        actions = listOf(ActionType.SHOW_NOTIFICATION),
        cooldownMinutes = cooldownMinutes,
        id = id,
    )
}
