package com.flowpilot.app.diagnostics

import com.flowpilot.app.actions.ActionDiagnostic
import com.flowpilot.app.actions.ActionDiagnosticReason
import com.flowpilot.app.actions.ActionResult
import com.flowpilot.app.actions.ActionResultCode
import com.flowpilot.app.actions.JsonCommandExecution
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class JsonCommandReasonTest {
    private val torch = """{"version":1,"action":"TORCH_ON","params":{}}"""

    private fun run(input: String, result: ActionResult = ActionResult(true, "ok")) = runBlocking {
        JsonCommandExecution.execute(input) { _, _ -> result }
    }

    @Test fun parseErrorsMapToStableCodesWithoutEchoingInput() {
        val garbage = run("{not json SECRET_JSON_SOURCE")
        assertThat(garbage.reasonCode).isEqualTo(ReasonCode.JSON_PARSE_ERROR)
        assertThat(garbage.detail.orEmpty()).doesNotContain("SECRET")
        assertThat(run("""{"version":1,"extra":1}""").reasonCode).isEqualTo(ReasonCode.JSON_INVALID_SCHEMA)
        assertThat(run("""{"version":2,"action":"TORCH_ON","params":{}}""").reasonCode).isEqualTo(ReasonCode.JSON_UNSUPPORTED_VERSION)
        assertThat(run("""{"version":1,"action":"NOPE","params":{}}""").reasonCode).isEqualTo(ReasonCode.JSON_UNSUPPORTED_ACTION)
        assertThat(run("""{"version":1,"action":"TORCH_ON","params":{"x":1}}""").reasonCode).isEqualTo(ReasonCode.JSON_INVALID_PARAMETERS)
    }

    @Test fun dispatchFailureAndNestedCausesAreDistinguished() {
        assertThat(run(torch, ActionResult(false, "boom")).reasonCode).isEqualTo(ReasonCode.JSON_DISPATCH_FAILED)
        val shizuku = run(torch, ActionResult(false, "x", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SHIZUKU_NOT_RUNNING)))
        assertThat(shizuku.reasonCode).isEqualTo(ReasonCode.SHIZUKU_NOT_RUNNING)
        assertThat(shizuku.detail).contains("nestedAction=TORCH_ON")
        assertThat(run(torch, ActionResult(true, "x", ActionResultCode.REQUEST_UNVERIFIED)).reasonCode).isEqualTo(ReasonCode.REQUEST_UNVERIFIED)
        assertThat(run(torch).reasonCode).isNull()
    }

    @Test fun outcomeConvertsToActionResultWithReason() {
        val result = run("{bad").toActionResult()
        assertThat(result.success).isFalse()
        assertThat(result.reasonCode).isEqualTo(ReasonCode.JSON_PARSE_ERROR)
    }
}
