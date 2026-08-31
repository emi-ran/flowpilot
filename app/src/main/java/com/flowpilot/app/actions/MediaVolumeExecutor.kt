package com.flowpilot.app.actions

import android.content.Context
import android.media.AudioManager
import com.flowpilot.app.data.model.ActionType
import kotlin.math.roundToInt

/** Sets music stream volume as a percentage of device-specific maximum volume. */
class MediaVolumeExecutor(
    context: Context,
    private val maxVolume: () -> Int = { context.getSystemService(AudioManager::class.java)?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0 },
    private val currentVolume: () -> Int = { context.getSystemService(AudioManager::class.java)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1 },
    private val setVolume: (Int) -> Unit = { level -> context.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) },
) : ActionExecutor {
    override val supportedTypes = setOf(ActionType.SET_MEDIA_VOLUME)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.SET_MEDIA_VOLUME) return ActionResult(false, "Unsupported action for media volume")
        val max = maxVolume()
        if (max <= 0) return ActionResult(false, "Media volume is unavailable")
        val percent = parameters.mediaVolumePercent.coerceIn(0, 100)
        val target = (max * percent / 100f).roundToInt()
        return try {
            setVolume(target)
            if (currentVolume() != target) return ActionResult(false, "Media volume change was blocked")
            ActionResult(true, "Media volume set to $percent%")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }
}
