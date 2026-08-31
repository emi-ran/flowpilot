package com.flowpilot.app.actions

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.SoundPreset

/** Plays selected current system sound or persisted user-selected audio URI. */
class SoundExecutor(
    private val context: Context,
    private val playUri: ((Uri, Int) -> Boolean)? = null,
) : ActionExecutor {
    private var activePlayer: MediaPlayer? = null

    override val supportedTypes = setOf(ActionType.PLAY_SOUND)

    private fun play(uri: Uri, durationMs: Int): Boolean {
        stopPreview()
        val player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            setDataSource(context, uri)
            setOnCompletionListener { completed ->
                if (activePlayer === completed) activePlayer = null
                completed.release()
            }
            setOnErrorListener { failed, _, _ ->
                if (activePlayer === failed) activePlayer = null
                failed.release()
                true
            }
            prepare()
            start()
        }
        activePlayer = player
        Handler(Looper.getMainLooper()).postDelayed(::stopPreview, durationMs.toLong())
        return player.isPlaying
    }

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.PLAY_SOUND) return ActionResult(false, "Unsupported action for sound")
        val uri = when (parameters.soundPreset) {
            SoundPreset.NOTIFICATION -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            SoundPreset.ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            SoundPreset.RINGTONE -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            SoundPreset.CUSTOM -> parameters.soundUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        } ?: return ActionResult(false, "Selected sound is unavailable")
        return try {
            val durationMs = parameters.soundDurationMs.coerceIn(1_000, 60_000)
            if ((playUri ?: ::play)(uri, durationMs)) ActionResult(true, "Sound played") else ActionResult(false, "Sound could not be played")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }

    fun stopPreview() {
        val player = activePlayer ?: return
        activePlayer = null
        try {
            if (player.isPlaying) player.stop()
        } catch (_: IllegalStateException) {
        } finally {
            player.release()
        }
    }
}
