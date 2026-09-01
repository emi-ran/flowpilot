package com.flowpilot.app.actions

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.flowpilot.app.data.model.ActionType

/**
 * Executes Sound Profile actions (Normal, Vibrate, Silent) via AudioManager.ringerMode.
 *
 * Uses:
 * - SOUND_PROFILE_NORMAL -> AudioManager.RINGER_MODE_NORMAL
 * - SOUND_PROFILE_VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
 * - SOUND_PROFILE_SILENT -> AudioManager.RINGER_MODE_SILENT
 *
 * On modern Android versions, toggling ringer modes (especially to/from silent or vibrate)
 * may affect Do Not Disturb / Notification policy, requiring Notification Policy Access.
 * Sets the target mode and reads back to ensure honest result reporting.
 */
class SoundProfileExecutor(
    private val context: Context,
    private val policyAccessChecker: (Context) -> Boolean = { ctx ->
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.isNotificationPolicyAccessGranted == true
    },
    private val ringerModeWriter: (Context, Int) -> Unit = { ctx, mode ->
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.ringerMode = mode
    },
    private val ringerModeReader: (Context) -> Int = { ctx ->
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.ringerMode
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SOUND_PROFILE_NORMAL,
        ActionType.SOUND_PROFILE_VIBRATE,
        ActionType.SOUND_PROFILE_SILENT,
    )

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (targetMode, modeName) = when (action) {
            ActionType.SOUND_PROFILE_NORMAL -> AudioManager.RINGER_MODE_NORMAL to "Normal"
            ActionType.SOUND_PROFILE_VIBRATE -> AudioManager.RINGER_MODE_VIBRATE to "Vibrate"
            ActionType.SOUND_PROFILE_SILENT -> AudioManager.RINGER_MODE_SILENT to "Silent"
            else -> return ActionResult(false, "Unsupported action for sound profile")
        }

        if (!policyAccessChecker(context)) {
            return ActionResult(
                false,
                "Sound profile requires Notification Policy Access permission",
            )
        }

        return try {
            ringerModeWriter(context, targetMode)
            val readBack = ringerModeReader(context)
            if (readBack != targetMode) {
                return ActionResult(
                    false,
                    "Sound profile state mismatch: requested $modeName ($targetMode) but read back $readBack",
                )
            }

            ActionResult(true, "Sound profile set to $modeName")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }
}
