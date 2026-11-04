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
}
