package com.flowpilot.app.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.flowpilot.app.actions.ActionResultCode
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManualRuleRunnerAuthorizationTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val repository by lazy { AutomationRepository(context) }
    private val runner by lazy { ManualRuleRunner(context, repository) }

    @After
    fun clean() = runTest {
        repository.rawDataStore.edit { it.clear() }
    }

    @Test
    fun savedShortcut_runsUnchangedEnabledRule() = runTest {
        val confirmed = addRule("unchanged")

        val result = runner.runSavedShortcut(confirmed, "en")

        assertThat(result.totalActions).isEqualTo(1)
        assertThat(result.successCount + result.failureCount).isEqualTo(1)
        val action = repository.executionHistory.first().first { it.ruleId == confirmed.id }.actions.single()
        assertThat(action.resultCode).isNotEqualTo(ActionResultCode.EXECUTION_CANCELLED)
    }

    @Test
    fun savedShortcut_rejectsDisabledEditedOrDeletedConfirmedRule() = runTest {
        for (mutation in Mutation.entries) {
            val confirmed = addRule(mutation.name)
            mutate(confirmed, mutation)

            val result = runner.runSavedShortcut(confirmed, "en")

            assertThat(result.totalActions).isEqualTo(1)
            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.failureCount).isEqualTo(1)
            val entry = repository.executionHistory.first().first { it.ruleId == confirmed.id }
            assertThat(entry.trigger).isEqualTo("MANUAL")
            assertThat(entry.actions).hasSize(1)
            assertThat(entry.actions.single().resultCode).isEqualTo(ActionResultCode.EXECUTION_CANCELLED)
            assertThat(entry.actions.single().success).isFalse()
        }
    }

    @Test
    fun savedShortcut_rejectsOldRevisionEvenAfterReenabled() = runTest {
        val confirmed = addRule("reenabled")
        repository.setEnabled(confirmed.id, false)
        repository.setEnabled(confirmed.id, true)

        val result = runner.runSavedShortcut(confirmed, "en")

        assertThat(result.successCount).isEqualTo(0)
        assertThat(result.failureCount).isEqualTo(1)
        assertThat(repository.executionHistory.first().first { it.ruleId == confirmed.id }
            .actions.single().resultCode).isEqualTo(ActionResultCode.EXECUTION_CANCELLED)
    }

    @Test
    fun savedGuard_rechecksEachStepAfterInterveningMutation() = runTest {
        for (mutation in Mutation.entries) {
            val confirmed = addRule("steps-${mutation.name}")
            val betweenSteps = CompletableDeferred<Unit>()
            val continueRun = CompletableDeferred<Unit>()
            val effects = mutableListOf<String>()
            val execution = async {
                repository.dispatchSavedRuleIfAuthorized(confirmed.id, confirmed.executionRevision) {
                    effects.add("first")
                }
                betweenSteps.complete(Unit)
                continueRun.await()
                repository.dispatchSavedRuleIfAuthorized(confirmed.id, confirmed.executionRevision) {
                    effects.add("second")
                }
            }
            betweenSteps.await()
            mutate(confirmed, mutation)
            continueRun.complete(Unit)

            assertThat(execution.await()).isNull()
            assertThat(effects).containsExactly("first")
        }
    }

    @Test
    fun draftRun_executesDisabledAndUnsavedSnapshots() = runTest {
        val saved = addRule("disabled-draft")
        repository.setEnabled(saved.id, false)
        val disabledDraft = repository.automations.first().first { it.id == saved.id }
        val unsavedDraft = disabledDraft.copy(id = "unsaved-draft")

        for (draft in listOf(disabledDraft, unsavedDraft)) {
            val result = runner.run(draft, "en")

            assertThat(result.totalActions).isEqualTo(1)
            assertThat(result.successCount + result.failureCount).isEqualTo(1)
            val entry = repository.executionHistory.first().first { it.ruleId == draft.id }
            assertThat(entry.actions).hasSize(1)
            assertThat(entry.actions.single().resultCode).isNotEqualTo(ActionResultCode.EXECUTION_CANCELLED)
        }
        assertThat(repository.automations.first().first { it.id == saved.id }.enabled).isFalse()
        assertThat(repository.automations.first().any { it.id == unsavedDraft.id }).isFalse()
    }

    private suspend fun addRule(id: String): Automation = repository.add(
        id = id,
        name = "Rule $id",
        triggerEvent = TriggerEvent.CHARGER_CONNECTED,
        appPackage = "",
        appName = "",
        actions = listOf(ActionType.SHOW_NOTIFICATION),
    )

    private suspend fun mutate(rule: Automation, mutation: Mutation) {
        when (mutation) {
            Mutation.DISABLE -> repository.setEnabled(rule.id, false)
            Mutation.EDIT -> repository.update(rule.copy(notificationTitle = "Changed"))
            Mutation.DELETE -> repository.delete(rule.id)
        }
    }

    private enum class Mutation { DISABLE, EDIT, DELETE }
}
