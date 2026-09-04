package com.flowpilot.app.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import com.flowpilot.app.data.AutomationRepository
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
class FlowPilotWidgetTest {

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

    @Test
    fun widgetToggleAction_togglesEngineState() = runTest {
        repository.setEngineEnabled(true)
        assertThat(repository.isEngineEnabled.first()).isTrue()

        val provider = FlowPilotWidgetProvider()

        provider.handleToggleEngine(context)
        assertThat(repository.isEngineEnabled.first()).isFalse()

        provider.handleToggleEngine(context)
        assertThat(repository.isEngineEnabled.first()).isTrue()
    }
}
