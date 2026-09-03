package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ActionDispatcherTest {

    @Test
    fun all_action_types_have_a_mapped_executor() {
        val dispatcher = ActionDispatcher.get(RuntimeEnvironment.getApplication())

        for (action in ActionType.entries) {
            val result = dispatcher.execute(action)
            // Even if an action fails because Shizuku or hardware is missing in the test environment,
            // it must NEVER fail with "No executor for ..."
            assertThat(result.message).doesNotContain("No executor for ${action.label}")
        }
    }
}
