package com.flowpilot.app.actions

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import com.flowpilot.app.data.model.ActionType

/**
 * Copies an already rendered text value to the system clipboard.
 *
 * Privacy: the clipped text is never logged, never included in result messages and never
 * persisted. On Android 13+ the clip is marked sensitive so system clipboard previews hide it.
 */
class ClipboardExecutor(
    private val context: Context,
    private val setPrimaryClip: (ClipData) -> Unit = { clip ->
        val manager = context.getSystemService(ClipboardManager::class.java)
            ?: error("ClipboardManager unavailable")
        manager.setPrimaryClip(clip)
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.COPY_TO_CLIPBOARD)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.COPY_TO_CLIPBOARD) return ActionResult(false, "Unsupported action for clipboard")
        val text = parameters.clipboardText
        validationReason(text)?.let { reason ->
            Log.w(TAG, "Clipboard copy rejected: ${reason.name}")
            return ActionResult(false, "Clipboard text is invalid", diagnostic = ActionDiagnostic(reason))
        }
        return try {
            setPrimaryClip(buildClip(text))
            Log.i(TAG, "Clipboard updated")
            ActionResult(true, "Copied to clipboard", ActionResultCode.COMPLETED)
        } catch (_: Exception) {
            Log.w(TAG, "Clipboard write failed")
            ActionResult(false, "Failed to copy to clipboard")
        }
    }

    companion object {
        private const val TAG = "ClipboardExecutor"

        /** Upper bound for the rendered clipboard value; matches the template length cap. */
        const val MAX_TEXT_LENGTH = TemplateRenderer.MAX_TEMPLATE_LENGTH

        /** Pure validation shared by the executor, the importer and the editor rules. */
        fun validationReason(text: String): ActionDiagnosticReason? = when {
            text.isBlank() -> ActionDiagnosticReason.INVALID_INPUT
            text.length > MAX_TEXT_LENGTH -> ActionDiagnosticReason.INVALID_INPUT
            else -> null
        }

        /** Sensitive-clip hint is honored by Android 13 (API 33) and later. */
        fun shouldMarkSensitive(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

        private fun buildClip(text: String): ClipData {
            val clip = ClipData.newPlainText("FlowPilot", text)
            if (shouldMarkSensitive(Build.VERSION.SDK_INT)) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            return clip
        }
    }
}
