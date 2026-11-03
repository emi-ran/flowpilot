package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationPackageFilterTest {

    @Test
    fun debugListenerIgnoresReleaseAndDebugPackages() {
        val debug = "com.flowpilot.app.debug"

        assertThat(isFlowPilotNotificationPackage("com.flowpilot.app", debug)).isTrue()
        assertThat(isFlowPilotNotificationPackage(debug, debug)).isTrue()
    }

    @Test
    fun releaseListenerIgnoresReleaseAndDebugPackages() {
        val release = "com.flowpilot.app"

        assertThat(isFlowPilotNotificationPackage(release, release)).isTrue()
        assertThat(isFlowPilotNotificationPackage("com.flowpilot.app.debug", release)).isTrue()
    }

    @Test
    fun unrelatedPackagesAreNotIgnored() {
        val debug = "com.flowpilot.app.debug"

        assertThat(isFlowPilotNotificationPackage("com.whatsapp", debug)).isFalse()
        assertThat(isFlowPilotNotificationPackage("com.flowpilot.application", debug)).isFalse()
        assertThat(isFlowPilotNotificationPackage("com.flowpilot.app.other", debug)).isFalse()
    }
}
