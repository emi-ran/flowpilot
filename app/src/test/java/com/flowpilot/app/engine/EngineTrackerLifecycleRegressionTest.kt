package com.flowpilot.app.engine

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class EngineTrackerLifecycleRegressionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun send(intent: Intent) {
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun batteryCacheUpdatesWithoutQueryAndClearsOnStop() {
        val tracker = BatteryLevelTracker(context)
        tracker.start()
        send(Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 42)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)
            .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals(42, tracker.currentLevel)
        assertEquals(true, tracker.isChargerConnected)
        tracker.stop()
        assertNull(tracker.currentLevel)
        assertNull(tracker.isChargerConnected)
    }

    @Test fun screenRestartDoesNotSuppressFirstRepeatedEvent() {
        val tracker = ScreenStateTracker(context)
        tracker.start()
        send(Intent(Intent.ACTION_SCREEN_OFF))
        assertEquals(listOf(ScreenEvent.OFF), tracker.drainEvents())
        tracker.stop()
        tracker.start()
        send(Intent(Intent.ACTION_SCREEN_OFF))
        assertEquals(listOf(ScreenEvent.OFF), tracker.drainEvents())
        tracker.stop()
    }

    @Test fun chargerRestartDoesNotSuppressFirstRepeatedEvent() {
        val tracker = ChargerStateTracker(context)
        tracker.start()
        send(Intent(Intent.ACTION_POWER_CONNECTED))
        assertEquals(listOf(ChargerEvent.CONNECTED), tracker.drainEvents())
        tracker.stop()
        tracker.start()
        send(Intent(Intent.ACTION_POWER_CONNECTED))
        assertEquals(listOf(ChargerEvent.CONNECTED), tracker.drainEvents())
        tracker.stop()
    }
}
