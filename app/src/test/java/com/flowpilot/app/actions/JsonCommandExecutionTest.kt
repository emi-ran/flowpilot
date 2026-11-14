package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class JsonCommandExecutionTest {
    @Test fun successFlowsToSubsequentWebhookAndSmsWithoutRerenderingPayload() = runBlocking {
        val command = """{"version":1,"id":"job-1","action":"SHOW_NOTIFICATION","params":{"title":"hello","body":"\u0024{sms.body}"}}"""
        var observed: ActionParameters? = null
        val outcome = JsonCommandExecution.execute(command) { action, params ->
            assertThat(action).isEqualTo(ActionType.SHOW_NOTIFICATION)
            observed = params
            ActionResult(true, "private content should not propagate")
        }
        assertThat(observed!!.notificationBody).isEqualTo("\${sms.body}")
        val context = outcome.applyTo(TemplateContext())
        val rule = Automation(id = "a", name = "a", createdAt = 1,
            webhookBody = "\${command.status}:\${command.id}:\${command.message}",
            smsMessage = "\${command.success}:\${command.result}")
        val next = ActionParametersFactory.create(rule, context)
        assertThat(next.webhookBody).isEqualTo("success:job-1:Command completed")
        assertThat(next.smsMessage).isEqualTo("true:Command completed")
        assertThat(outcome.toString()).doesNotContain("private content")
    }

    @Test fun failuresAreSafeAndReplacePreviousResult() = runBlocking {
        val previous = CommandOutcome(true, "success", "Command completed", "old").applyTo(TemplateContext())
        val outcome = JsonCommandExecution.execute("secret invalid input") { _, _ -> error("must not dispatch") }
        val context = outcome.applyTo(previous)
        assertThat(context.values["command.success"]).isEqualTo("false")
        assertThat(context.values["command.status"]).isEqualTo("invalid_command")
        assertThat(context.values["command.id"]).isEmpty()
        assertThat(outcome.toString()).doesNotContain("secret")
    }

    @Test fun permissionDiagnosticsAreClassifiedWithoutExposingExecutorMessages() = runBlocking {
        val command = """{"version":1,"action":"TORCH_ON","params":{}}"""
        val permissionReasons = setOf(
            ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED,
            ActionDiagnosticReason.BLUETOOTH_PERMISSION_REQUIRED,
            ActionDiagnosticReason.SETTINGS_PERMISSION_REQUIRED,
            ActionDiagnosticReason.DND_PERMISSION_REQUIRED,
            ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED,
            ActionDiagnosticReason.PHONE_PERMISSION_REQUIRED,
            ActionDiagnosticReason.SMS_PERMISSION_REQUIRED,
        )
        for (reason in ActionDiagnosticReason.values()) {
            val outcome = JsonCommandExecution.execute(command) { _, _ ->
                ActionResult(false, "private executor details", diagnostic = ActionDiagnostic(reason))
            }
            val permission = reason in permissionReasons
            assertThat(outcome.status).isEqualTo(if (permission) "permission_required" else "execution_failed")
            assertThat(outcome.message).isEqualTo(if (permission) "Permission required" else "Command execution failed")
            assertThat(outcome.toString()).doesNotContain("private executor details")
        }
        val withoutDiagnostic = JsonCommandExecution.execute(command) { _, _ -> ActionResult(false, "private details") }
        assertThat(withoutDiagnostic.status).isEqualTo("execution_failed")
        val success = JsonCommandExecution.execute(command) { _, _ ->
            ActionResult(true, "private details", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SETTINGS_PERMISSION_REQUIRED))
        }
        assertThat(success.status).isEqualTo("success")
        assertThat(success.message).isEqualTo("Command completed")
    }

    @Test fun permissionFailureAndMusicSuccessDoNotClaimPlayback() = runBlocking {
        val torch = """{"version":1,"action":"TORCH_ON","params":{}}"""
        val denied = JsonCommandExecution.execute(torch) { _, _ -> ActionResult(false, "private exception", ActionResultCode.PERMISSION_REQUIRED) }
        assertThat(denied.status).isEqualTo("permission_required")
        assertThat(denied.message).isEqualTo("Permission required")
        val music = """{"version":1,"action":"MUSIC_SEARCH","params":{"appPackage":"com.spotify.music","query":"song"}}"""
        val result = JsonCommandExecution.execute(music) { _, _ -> ActionResult(true, "ok") }
        assertThat(result.message).contains("playback not verified")
    }
}
