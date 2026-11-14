package com.flowpilot.app.permission

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationListenerStatusTest {
    @Test
    fun permissionTakesPrecedenceOverConnection() {
        assertThat(notificationListenerStatus(accessGranted = false, connected = false))
            .isEqualTo(CapabilityStatus.PERMISSION_REQUIRED)
        assertThat(notificationListenerStatus(accessGranted = false, connected = true))
            .isEqualTo(CapabilityStatus.PERMISSION_REQUIRED)
    }

    @Test
    fun grantedAccessDistinguishesDisconnectedAndConnected() {
        assertThat(notificationListenerStatus(accessGranted = true, connected = false))
            .isEqualTo(CapabilityStatus.LISTENER_DISCONNECTED)
        assertThat(notificationListenerStatus(accessGranted = true, connected = true))
            .isEqualTo(CapabilityStatus.AVAILABLE)
    }
}
