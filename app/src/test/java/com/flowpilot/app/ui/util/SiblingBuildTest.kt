package com.flowpilot.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SiblingBuildTest {
    @Test
    fun releaseMapsToDebug() {
        assertEquals("com.flowpilot.app.debug", SiblingBuild.siblingPackageName("com.flowpilot.app"))
    }

    @Test
    fun debugMapsToRelease() {
        assertEquals("com.flowpilot.app", SiblingBuild.siblingPackageName("com.flowpilot.app.debug"))
    }
}
