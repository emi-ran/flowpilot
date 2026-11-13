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

        val result = executor.execute(ActionType.SHOW_NOTIFICATION, ActionParameters(notificationTitle = "Low battery", notificationBody = "Enable charger"))

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Notification posted")
        assertThat(posted).isNotNull()
    }

    @Test fun execute_returnsFailure_whenPermissionMissing() {
        var posted = false
        val executor = NotificationExecutor(
            context = RuntimeEnvironment.getApplication(),
            permissionChecker = { false },
            poster = { _, _ -> posted = true },
        )

        val result = executor.execute(ActionType.SHOW_NOTIFICATION, ActionParameters(notificationTitle = "Title", notificationBody = "Body"))

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("permission")
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED)
        assertThat(result.resultCode).isEqualTo(ActionResultCode.PERMISSION_REQUIRED)
        assertThat(posted).isFalse()
    }
}
