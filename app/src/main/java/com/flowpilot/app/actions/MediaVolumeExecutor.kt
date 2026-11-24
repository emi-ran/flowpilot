package com.flowpilot.app.actions

import android.content.Context
import android.media.AudioManager
import android.os.Build
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.engine.OwnSoundChangeGuard
import kotlin.math.roundToInt

/** Sets a stream volume as a percentage of its device-specific maximum. */
class MediaVolumeExecutor(
    context: Context,
    private val maxVolume: (Int) -> Int = { stream -> context.getSystemService(AudioManager::class.java)?.getStreamMaxVolume(stream) ?: 0 },
    private val minVolume: (Int) -> Int = { stream ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) context.getSystemService(AudioManager::class.java)?.getStreamMinVolume(stream) ?: 0 else 0
    },
    private val currentVolume: (Int) -> Int = { stream -> context.getSystemService(AudioManager::class.java)?.getStreamVolume(stream) ?: -1 },
    private val setVolume: (Int, Int) -> Unit = { stream, level -> context.getSystemService(AudioManager::class.java)?.setStreamVolume(stream, level, 0) },
) : ActionExecutor {
    override val supportedTypes = setOf(ActionType.SET_MEDIA_VOLUME, ActionType.SET_NOTIFICATION_VOLUME, ActionType.SET_ALARM_VOLUME)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (stream, percent, name) = when (action) {
            ActionType.SET_MEDIA_VOLUME -> Triple(AudioManager.STREAM_MUSIC, parameters.mediaVolumePercent, "Media")
            ActionType.SET_NOTIFICATION_VOLUME -> Triple(AudioManager.STREAM_NOTIFICATION, parameters.notificationVolumePercent, "Notification")
            ActionType.SET_ALARM_VOLUME -> Triple(AudioManager.STREAM_ALARM, parameters.alarmVolumePercent, "Alarm")
            else -> return ActionResult(false, "Unsupported action for volume")
        }
        return try {
            val max = maxVolume(stream)
            val min = minVolume(stream)
            if (max <= 0 || min !in 0..max) return ActionResult(false, "$name volume is unavailable", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE))
            val requestedPercent = percent.coerceIn(0, 100)
            val requested = (max * requestedPercent / 100f).roundToInt()
            if (requested < min) return ActionResult(false, "$name volume is below the device minimum", diagnostic = ActionDiagnostic(ActionDiagnosticReason.VOLUME_BELOW_MINIMUM))
            // Loop guard: volume changes can flip the ringer mode or Do Not Disturb.
            OwnSoundChangeGuard.markOwnChange()
            try { setVolume(stream, requested) } finally { OwnSoundChangeGuard.markOwnChange() }
            val actual = currentVolume(stream)
            if (actual !in min..max) return ActionResult(false, "$name volume is unavailable", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE))
            if (actual != requested) return ActionResult(false, "$name volume change was blocked or limited", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_MISMATCH))
            ActionResult(true, "$name volume set to $requestedPercent%")
        } catch (_: SecurityException) {
            ActionResult(false, "$name volume change requires system permission or Do Not Disturb access", ActionResultCode.PERMISSION_REQUIRED, diagnostic = ActionDiagnostic(ActionDiagnosticReason.VOLUME_PERMISSION_REQUIRED))
        } catch (_: Exception) {
            ActionResult(false, "$name volume change failed", diagnostic = ActionDiagnostic(ActionDiagnosticReason.SYSTEM_COMMAND_FAILED))
        }
    }
}
