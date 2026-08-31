package com.flowpilot.app.actions

import android.content.ComponentName
import android.content.Intent
import android.provider.AlarmClock
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockExecutorTest {

    @Test
    fun execute_unsupportedAction_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = ClockExecutor(context = context)

        val result = executor.execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for Clock")
    }

    @Test
    fun execute_createAlarm_invalidTime_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = ClockExecutor(context = context)

        val result = executor.execute(
            ActionType.CREATE_ALARM,
            ActionParameters(alarmHour = 25, alarmMinute = 0),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Invalid alarm time")
    }

    @Test
    fun execute_createAlarm_noHandler_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = ClockExecutor(
            context = context,
            intentResolver = { _, _ -> null },
        )

        val result = executor.execute(
            ActionType.CREATE_ALARM,
            ActionParameters(alarmHour = 8, alarmMinute = 30),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("No Clock application available")
    }

    @Test
    fun execute_createAlarm_resolvesAndDispatchesIntent() {
        val context = RuntimeEnvironment.getApplication()
        var startedIntent: Intent? = null

        val executor = ClockExecutor(
            context = context,
            intentResolver = { _, _ -> ComponentName("com.android.deskclock", "com.android.deskclock.HandleSetAlarmActivity") },
            activityStarter = { _, intent -> startedIntent = intent },
        )

        val result = executor.execute(
            ActionType.CREATE_ALARM,
            ActionParameters(alarmHour = 7, alarmMinute = 15, alarmMessage = "Morning Wakeup"),
        )

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Alarm request sent to Clock app for 07:15")
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent!!.action).isEqualTo(AlarmClock.ACTION_SET_ALARM)
        assertThat(startedIntent!!.getIntExtra(AlarmClock.EXTRA_HOUR, -1)).isEqualTo(7)
        assertThat(startedIntent!!.getIntExtra(AlarmClock.EXTRA_MINUTES, -1)).isEqualTo(15)
        assertThat(startedIntent!!.getStringExtra(AlarmClock.EXTRA_MESSAGE)).isEqualTo("Morning Wakeup")
        assertThat(startedIntent!!.hasExtra(AlarmClock.EXTRA_SKIP_UI)).isFalse()
        assertThat(startedIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun execute_startTimer_invalidDuration_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = ClockExecutor(context = context)

        val resultLow = executor.execute(
            ActionType.START_TIMER,
            ActionParameters(timerDurationSeconds = 0),
        )
        assertThat(resultLow.success).isFalse()
        assertThat(resultLow.message).contains("Invalid timer duration")

        val resultHigh = executor.execute(
            ActionType.START_TIMER,
            ActionParameters(timerDurationSeconds = 100000),
        )
        assertThat(resultHigh.success).isFalse()
        assertThat(resultHigh.message).contains("Invalid timer duration")
    }

    @Test
    fun execute_startTimer_noHandler_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = ClockExecutor(
            context = context,
            intentResolver = { _, _ -> null },
        )

        val result = executor.execute(
            ActionType.START_TIMER,
            ActionParameters(timerDurationSeconds = 600),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("No Clock application available")
    }

    @Test
    fun execute_startTimer_resolvesAndDispatchesIntent() {
        val context = RuntimeEnvironment.getApplication()
        var startedIntent: Intent? = null

        val executor = ClockExecutor(
            context = context,
            intentResolver = { _, _ -> ComponentName("com.android.deskclock", "com.android.deskclock.HandleSetAlarmActivity") },
            activityStarter = { _, intent -> startedIntent = intent },
        )

        val result = executor.execute(
            ActionType.START_TIMER,
            ActionParameters(timerDurationSeconds = 300, timerMessage = "Tea timer"),
        )

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Timer request sent to Clock app (300 seconds)")
        assertThat(startedIntent).isNotNull()
        assertThat(startedIntent!!.action).isEqualTo(AlarmClock.ACTION_SET_TIMER)
        assertThat(startedIntent!!.getIntExtra(AlarmClock.EXTRA_LENGTH, -1)).isEqualTo(300)
        assertThat(startedIntent!!.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)).isTrue()
        assertThat(startedIntent!!.getStringExtra(AlarmClock.EXTRA_MESSAGE)).isEqualTo("Tea timer")
        assertThat(startedIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun execute_whenStarterThrows_returnsFailureWithMessage() {
        val context = RuntimeEnvironment.getApplication()

        val executor = ClockExecutor(
            context = context,
            intentResolver = { _, _ -> ComponentName("com.android.deskclock", "com.android.deskclock.HandleSetAlarmActivity") },
            activityStarter = { _, _ -> throw SecurityException("Activity start blocked") },
        )

        val result = executor.execute(
            ActionType.CREATE_ALARM,
            ActionParameters(alarmHour = 6, alarmMinute = 0),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Activity start blocked")
    }
}
