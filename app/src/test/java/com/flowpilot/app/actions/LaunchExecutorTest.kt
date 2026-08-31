package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchExecutorTest {
    @Test fun launchApp_returnsFailure_whenTargetIsEmpty() {
        val result = LaunchExecutor(RuntimeEnvironment.getApplication()).execute(ActionType.LAUNCH_APP)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Choose an app")
    }

    @Test fun openUrl_rejectsNonHttpSchemes() {
        val result = LaunchExecutor(RuntimeEnvironment.getApplication()).execute(
            ActionType.OPEN_URL,
            ActionParameters(url = "intent://settings"),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("valid http or https")
    }
}
