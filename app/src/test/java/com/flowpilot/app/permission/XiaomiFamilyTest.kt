package com.flowpilot.app.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiFamilyTest {
    @Test
    fun detectsXiaomiFamilyCaseInsensitively() {
        assertTrue(isXiaomiFamily("Xiaomi"))
        assertTrue(isXiaomiFamily("REDMI"))
        assertTrue(isXiaomiFamily(" poco "))
    }

    @Test
    fun rejectsOtherManufacturers() {
        assertFalse(isXiaomiFamily("samsung"))
        assertFalse(isXiaomiFamily(""))
        assertFalse(isXiaomiFamily(null))
    }

    @Test
    fun hintOnlyWhenDisconnectedOnXiaomi() {
        assertTrue(shouldShowMiuiAutostartHint("xiaomi", CapabilityStatus.LISTENER_DISCONNECTED))
        assertFalse(shouldShowMiuiAutostartHint("xiaomi", CapabilityStatus.AVAILABLE))
        assertFalse(shouldShowMiuiAutostartHint("xiaomi", CapabilityStatus.PERMISSION_REQUIRED))
        assertFalse(shouldShowMiuiAutostartHint("samsung", CapabilityStatus.LISTENER_DISCONNECTED))
    }
}
