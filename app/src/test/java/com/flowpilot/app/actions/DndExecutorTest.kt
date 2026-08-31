package com.flowpilot.app.actions

import android.app.NotificationManager
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DndExecutorTest {

    @Test
    fun execute_unsupportedAction_returnsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val executor = DndExecutor(context = context)

        val result = executor.execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for Do Not Disturb")
    }

    @Test
    fun execute_whenPolicyAccessDenied_returnsFailureWithoutWriting() {
        val context = RuntimeEnvironment.getApplication()
        var writeAttempted = false

        val executor = DndExecutor(
            context = context,
            policyAccessChecker = { false },
            interruptionFilterWriter = { _, _ ->
                writeAttempted = true
            },
        )

        val result = executor.execute(ActionType.DND_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Notification Policy Access")
        assertThat(writeAttempted).isFalse()
    }

    @Test
    fun execute_dndOn_writesFilterNoneAndReadsBack() {
        val context = RuntimeEnvironment.getApplication()
        var writtenFilter: Int? = null
        var currentFilter = NotificationManager.INTERRUPTION_FILTER_ALL

        val executor = DndExecutor(
            context = context,
            policyAccessChecker = { true },
            interruptionFilterWriter = { _, filter ->
                writtenFilter = filter
                currentFilter = filter
            },
            interruptionFilterReader = { currentFilter },
        )

        val result = executor.execute(ActionType.DND_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Do Not Disturb turned on")
        assertThat(writtenFilter).isEqualTo(NotificationManager.INTERRUPTION_FILTER_NONE)
    }

    @Test
    fun execute_dndOff_writesFilterAllAndReadsBack() {
        val context = RuntimeEnvironment.getApplication()
        var writtenFilter: Int? = null
        var currentFilter = NotificationManager.INTERRUPTION_FILTER_NONE

        val executor = DndExecutor(
            context = context,
            policyAccessChecker = { true },
            interruptionFilterWriter = { _, filter ->
                writtenFilter = filter
                currentFilter = filter
            },
            interruptionFilterReader = { currentFilter },
        )

        val result = executor.execute(ActionType.DND_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Do Not Disturb turned off")
        assertThat(writtenFilter).isEqualTo(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test
    fun execute_whenReadbackMismatches_returnsFailureHonestMismatch() {
        val context = RuntimeEnvironment.getApplication()

        val executor = DndExecutor(
            context = context,
            policyAccessChecker = { true },
            interruptionFilterWriter = { _, _ -> },
            interruptionFilterReader = { NotificationManager.INTERRUPTION_FILTER_ALL }, // returns ALL even when requested NONE
        )

        val result = executor.execute(ActionType.DND_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("state mismatch")
    }

    @Test
    fun execute_whenExceptionThrown_returnsFailureWithMessage() {
        val context = RuntimeEnvironment.getApplication()

        val executor = DndExecutor(
            context = context,
            policyAccessChecker = { true },
            interruptionFilterWriter = { _, _ -> throw SecurityException("Mock DND security block") },
        )

        val result = executor.execute(ActionType.DND_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Mock DND security block")
    }
}
