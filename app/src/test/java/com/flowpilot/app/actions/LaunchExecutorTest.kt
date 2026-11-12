package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionExecutionRecord
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.INVALID_INPUT)
    }

    @Test fun openUrl_rejectsNonHttpSchemes() {
        val result = LaunchExecutor(RuntimeEnvironment.getApplication()).execute(
            ActionType.OPEN_URL,
            ActionParameters(url = "intent://settings"),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("valid http or https")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.INVALID_INPUT)
    }

    @Test fun launchApp_missingTargetReportsUnavailable() {
        val result = LaunchExecutor(RuntimeEnvironment.getApplication()).execute(
            ActionType.LAUNCH_APP, ActionParameters(launchPackage = "missing.package"),
        )
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.TARGET_APP_UNAVAILABLE)
    }
    @Test fun invalid_input_and_missing_app_are_distinct_and_private_in_history() {
        val executor = LaunchExecutor(RuntimeEnvironment.getApplication())
        val privateUrl = "intent://private.example/path?token=secret"
        val privatePackage = "private.missing.application"
        val invalid = executor.execute(ActionType.OPEN_URL, ActionParameters(url = privateUrl))
        val missing = executor.execute(ActionType.LAUNCH_APP, ActionParameters(launchPackage = privatePackage))
        listOf(
            Triple(ActionType.OPEN_URL, invalid, ActionDiagnosticReason.INVALID_INPUT),
            Triple(ActionType.LAUNCH_APP, missing, ActionDiagnosticReason.TARGET_APP_UNAVAILABLE),
        ).forEach { (action, result, reason) ->
            val record = ActionExecutionRecord.create(action, result)
            assertThat(record.success).isFalse()
            assertThat(record.diagnostic?.reason).isEqualTo(reason)
            val encoded = Json.encodeToString(record)
            assertThat(encoded).doesNotContain(privateUrl)
            assertThat(encoded).doesNotContain("secret")
            assertThat(encoded).doesNotContain(privatePackage)
            assertThat(Json.decodeFromString<ActionExecutionRecord>(encoded).diagnostic).isEqualTo(record.diagnostic)
        }
    }
}
