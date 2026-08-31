package com.flowpilot.app.actions

import android.os.VibrationEffect
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.VibrationPattern
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VibrationExecutorTest {
    @Test fun execute_playsConfiguredPattern_whenVibratorExists() {
        var effect: VibrationEffect? = null
        val result = VibrationExecutor(
            RuntimeEnvironment.getApplication(),
            hasVibrator = { true },
            play = { effect = it },
        ).execute(
            ActionType.VIBRATE,
            ActionParameters(vibrationPattern = VibrationPattern.DOUBLE_TAP, vibrationDurationMs = 200, vibrationAmplitude = 220),
        )

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Vibration played")
        assertThat(effect).isNotNull()
    }

    @Test fun execute_returnsFailure_whenVibratorMissing() {
        val result = VibrationExecutor(RuntimeEnvironment.getApplication(), hasVibrator = { false }).execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("no vibrator")
    }
}
