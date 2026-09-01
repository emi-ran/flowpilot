package com.flowpilot.app.ui

import com.flowpilot.app.actions.ActionResult
import com.flowpilot.app.actions.WebhookExecutor
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ManualRunResultTest {

    @Test
    fun manualRunResult_success_formatsCorrectly() {
        val result = ManualRunResult(
            totalActions = 2,
            successCount = 2,
            failureCount = 0,
            failureMessages = emptyList(),
        )

        assertThat(result.totalActions).isEqualTo(2)
        assertThat(result.successCount).isEqualTo(2)
        assertThat(result.failureCount).isEqualTo(0)
        assertThat(result.failureMessages).isEmpty()
    }

    @Test
    fun manualRunResult_failureMessages_areRedacted() {
        val rawSecretUrl = "http://admin:secret123@example.com/api?token=secrettoken&api_key=mykey"
        val rawErrorMessage = "HTTP request failed to $rawSecretUrl: Bearer my_jwt_token"

        val redacted = WebhookExecutor.redactSensitiveText(rawErrorMessage)

        val result = ManualRunResult(
            totalActions = 1,
            successCount = 0,
            failureCount = 1,
            failureMessages = listOf("${ActionType.HTTP_WEBHOOK.label}: $redacted"),
        )

        assertThat(result.failureCount).isEqualTo(1)
        assertThat(result.failureMessages).hasSize(1)
        val msg = result.failureMessages.first()
        assertThat(msg).doesNotContain("secret123")
        assertThat(msg).doesNotContain("secrettoken")
        assertThat(msg).doesNotContain("mykey")
        assertThat(msg).doesNotContain("my_jwt_token")
        assertThat(msg).contains("[REDACTED]")
    }
}
