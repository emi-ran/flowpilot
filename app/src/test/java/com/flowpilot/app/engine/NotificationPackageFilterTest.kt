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

    @Test
    fun hyperosVolumeUiNotificationsAreIgnored() {
        val release = "com.flowpilot.app"

        assertThat(isIgnoredNotificationPackage("com.miui.misound", release)).isTrue()
        assertThat(isIgnoredNotificationPackage("com.whatsapp", release)).isFalse()
    }

    @Test
    fun systemPackageFilterOnlyMatchesTheVolumeUiPackage() {
        assertThat(isIgnoredSystemNotificationPackage("com.miui.misound")).isTrue()
        assertThat(isIgnoredSystemNotificationPackage("com.miui.misound.extra")).isFalse()
        assertThat(isIgnoredSystemNotificationPackage("com.miui.misoundx")).isFalse()
    }

    @Test
    fun lookalikeFlowPilotPackagesAreNotTreatedAsSiblings() {
        val debug = "com.flowpilot.app.debug"

        assertThat(isFlowPilotNotificationPackage("com.flowpilot.app.other.debug", debug)).isFalse()
        assertThat(isIgnoredNotificationPackage("com.flowpilot.app.other.debug", debug)).isFalse()
    }
}
