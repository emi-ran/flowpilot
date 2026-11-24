package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationListenerRebindGateTest {
    private val interval = 30_000L

    @Test
    fun connectedOrDeniedListenerNeverRequestsRebind() {
        val gate = NotificationListenerRebindGate(interval)

        assertThat(gate.shouldAttempt(connected = true, accessGranted = true, nowMs = 1_000L)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = false, nowMs = 1_000L)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L)).isTrue()
    }

    @Test
    fun disconnectedListenerRetriesOnlyAfterInterval() {
        val gate = NotificationListenerRebindGate(interval)

        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L)).isTrue()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_001L)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L + interval - 1)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L + interval)).isTrue()
    }

    @Test
    fun deniedAccessDoesNotConsumeRetryWindow() {
        val gate = NotificationListenerRebindGate(interval)

        assertThat(gate.shouldAttempt(connected = false, accessGranted = false, nowMs = 1_000L)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L)).isTrue()
    }

    @Test
    fun revokedAccessDoesNotResetRetryWindow() {
        val gate = NotificationListenerRebindGate(interval)

        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L)).isTrue()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = false, nowMs = 1_500L)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 2_500L)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L + interval)).isTrue()
    }

    @Test
    fun becomingConnectedDoesNotResetRetryWindow() {
        val gate = NotificationListenerRebindGate(interval)

        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L)).isTrue()
        assertThat(gate.shouldAttempt(connected = true, accessGranted = true, nowMs = 2_000L)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 2_000L)).isFalse()
    }

    @Test
    fun repeatedFailedRebindsBackOffAndCapAtMaxInterval() {
        val gate = NotificationListenerRebindGate(baseIntervalMs = interval, maxIntervalMs = 120_000L)

        var now = 1_000L
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now)).isTrue()

        // 2nd attempt allowed after 30s
        now += 30_000L
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now - 1)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now)).isTrue()

        // 3rd attempt backed off to 60s
        now += 60_000L
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now - 1)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now)).isTrue()

        // 4th attempt capped at 120s
        now += 120_000L
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now - 1)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now)).isTrue()

        // 5th attempt remains capped at 120s
        now += 120_000L
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now - 1)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = now)).isTrue()
    }

    @Test
    fun resetAndForceAllowImmediateRebindWithinCooldown() {
        val gate = NotificationListenerRebindGate(baseIntervalMs = interval, maxIntervalMs = 300_000L)

        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 1_000L)).isTrue()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 2_000L, force = true)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 3_000L, force = true)).isTrue()

        // After force reset, normal backoff starts from base interval again
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 3_000L + interval - 1)).isFalse()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 3_000L + interval)).isTrue()

        gate.reset()
        assertThat(gate.shouldAttempt(connected = false, accessGranted = true, nowMs = 4_000L + interval)).isTrue()
    }

    @Test
    fun overlappingInstanceDestroyDoesNotClobberReplacementConnection() {
        val tracker = NotificationListenerConnectionTracker()
        val oldInstance = Any()
        val newInstance = Any()

        assertThat(tracker.onConnected(oldInstance)).isTrue()
        assertThat(tracker.isConnected()).isTrue()

        // Replacement service instance connects before old instance finishes onDestroy
        assertThat(tracker.onConnected(newInstance)).isTrue()
        assertThat(tracker.onDisconnected(oldInstance)).isTrue()
        assertThat(tracker.isConnected()).isTrue()

        // Disconnecting the active replacement instance marks listener disconnected
        assertThat(tracker.onDisconnected(newInstance)).isFalse()
        assertThat(tracker.isConnected()).isFalse()
    }

    @Test
    fun currentTracksLatestLiveInstanceAndClearsOnDisconnect() {
        val tracker = NotificationListenerConnectionTracker()
        val first = Any()
        val second = Any()
        assertThat(tracker.current()).isNull()
        tracker.onConnected(first)
        assertThat(tracker.current()).isSameInstanceAs(first)
        tracker.onConnected(second)
        assertThat(tracker.current()).isSameInstanceAs(second)
        tracker.onDisconnected(first)
        assertThat(tracker.current()).isSameInstanceAs(second)
        tracker.onDisconnected(second)
        assertThat(tracker.current()).isNull()
        tracker.onConnected(first)
        tracker.clear()
        assertThat(tracker.current()).isNull()
    }
}
