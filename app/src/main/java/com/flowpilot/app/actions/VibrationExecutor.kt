package com.flowpilot.app.actions

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.VibrationPattern

/** Plays finite vibration patterns with strength when device hardware supports amplitude control. */
class VibrationExecutor(
    private val context: Context,
    private val hasVibrator: () -> Boolean = {
        context.getSystemService(Vibrator::class.java)?.hasVibrator() == true
    },
    private val play: (VibrationEffect) -> Unit = { effect ->
        context.getSystemService(Vibrator::class.java)?.vibrate(effect)
    },
) : ActionExecutor {
    override val supportedTypes = setOf(ActionType.VIBRATE)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.VIBRATE) return ActionResult(false, "Unsupported action for vibration")
        if (!hasVibrator()) return ActionResult(false, "This device has no vibrator")
        val duration = parameters.vibrationDurationMs.coerceIn(80, 800).toLong()
        val amplitude = parameters.vibrationAmplitude.coerceIn(1, 255)
        return try {
            val effect = when (parameters.vibrationPattern) {
                VibrationPattern.PULSE -> VibrationEffect.createOneShot(duration, amplitude)
                VibrationPattern.DOUBLE_TAP -> weightedWaveform(duration, intArrayOf(2, 1, 2), amplitude)
                VibrationPattern.ALERT -> weightedWaveform(duration, intArrayOf(2, 1, 2, 1, 2), amplitude)
                VibrationPattern.HEARTBEAT -> weightedWaveform(duration, intArrayOf(2, 1, 4), amplitude)
                VibrationPattern.TRIPLE_TAP -> weightedWaveform(duration, intArrayOf(1, 1, 1, 1, 1), amplitude)
                VibrationPattern.SOS -> weightedWaveform(
                    duration,
                    intArrayOf(1, 1, 1, 1, 1, 2, 3, 2, 3, 2, 3, 1, 1, 1, 1, 1, 1),
                    amplitude,
                )
            }
            play(effect)
            ActionResult(true, "Vibration played")
        } catch (t: Throwable) {
            ActionResult(false, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun waveform(timings: LongArray, amplitude: Int): VibrationEffect =
        VibrationEffect.createWaveform(timings, IntArray(timings.size) { if (it % 2 == 0) 0 else amplitude }, -1)

    /** Splits selected total duration across on/off rhythm weights without exceeding it. */
    private fun weightedWaveform(totalDuration: Long, weights: IntArray, amplitude: Int): VibrationEffect {
        val totalWeight = weights.sum()
        var allocated = 0L
        val timings = LongArray(weights.size + 1)
        timings[0] = 0L
        weights.forEachIndexed { index, weight ->
            val slot = if (index == weights.lastIndex) totalDuration - allocated
            else totalDuration * weight / totalWeight
            timings[index + 1] = slot
            allocated += slot
        }
        return waveform(timings, amplitude)
    }
}
