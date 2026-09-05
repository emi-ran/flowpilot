package com.flowpilot.app.engine

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.datastore.preferences.core.edit
import com.flowpilot.app.data.AutomationRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineControlTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun clean() = runTest {
        AutomationService.setEnabled(context, false)
        AutomationRepository(context).rawDataStore.edit { it.clear() }
    }

    @Test
    fun concurrentRepositoryToggles_preserveBothChanges() = runTest {
        val first = AutomationRepository(context)
        val second = AutomationRepository(context)
        first.setEngineEnabled(true)
        val gate = CompletableDeferred<Unit>()
        val results = listOf(first, second).map { repository ->
            async(Dispatchers.IO) { gate.await(); repository.toggleEngineEnabled() }
        }
        gate.complete(Unit)
        assertThat(results.awaitAll()).containsExactly(false, true)
        assertThat(first.isEngineEnabled.first()).isTrue()
    }

    @Test
    fun concurrentControls_lastCommandMatchesCommittedPreference() = runTest {
        val commands = Collections.synchronizedList(mutableListOf<Boolean>())
        val recording = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName {
                Thread.sleep(10) // Keep command delivery open while other callers race.
                commands.add(true)
                return ComponentName(baseContext, AutomationService::class.java)
            }
            override fun stopService(service: Intent): Boolean {
                commands.add(false)
                return true
            }
        }
        AutomationRepository(context).setEngineEnabled(false)
        val gate = CompletableDeferred<Unit>()
        val calls = (0..2).map { index -> async(Dispatchers.IO) {
            gate.await()
            if (index == 2) AutomationService.setEnabled(recording, false)
            else AutomationService.toggleEnabled(recording)
        } }
        gate.complete(Unit)
        calls.awaitAll()
        assertThat(commands).hasSize(3)
        assertThat(commands.last()).isEqualTo(AutomationRepository(context).isEngineEnabled.first())
        assertThat(AutomationService.running.value).isFalse() // Accepted request is not a running engine.
    }

    @Test
    fun rejectedStartup_retainsIntentAndPublishesFailureWithoutNotificationService() = runTest {
        val rejecting = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                throw SecurityException("Rejected by test")
            }
            override fun getSystemService(name: String): Any? =
                if (name == Context.NOTIFICATION_SERVICE) null else super.getSystemService(name)
        }
        assertThat(AutomationService.setEnabled(rejecting, true)).isFalse()
        assertThat(AutomationRepository(context).isEngineEnabled.first()).isTrue()
        assertThat(AutomationService.running.value).isFalse()
        assertThat(AutomationService.failure.value).isTrue()
        AutomationService.loadFailure(context)
        assertThat(AutomationService.failure.value).isTrue()
        AutomationService.setEnabled(context, false)
        assertThat(AutomationService.failure.value).isFalse()
    }
}
