package com.flowpilot.app.actions

import android.app.Notification
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationExecutorTest {
    @Test fun execute_postsConfiguredNotification_whenPermissionGranted() {
        var posted: Pair<Int, Notification>? = null
        val executor = NotificationExecutor(
            context = RuntimeEnvironment.getApplication(),
            permissionChecker = { true },
            poster = { id, notification -> posted = id to notification },
        )

        val result = executor.execute(ActionType.SHOW_NOTIFICATION, "Low battery", "Enable charger")

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Notification posted")
        assertThat(posted).isNotNull()
    }

    @Test fun execute_returnsFailure_whenPermissionMissing() {
        val executor = NotificationExecutor(
            context = RuntimeEnvironment.getApplication(),
            permissionChecker = { false },
        )

        val result = executor.execute(ActionType.SHOW_NOTIFICATION, "Title", "Body")

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("permission")
    }
}
