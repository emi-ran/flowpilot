package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class SensitiveEventQueueTest {
    private val now = 1_000_000L

    @After
    fun clearTransientState() {
        SmsEventTracker.clear()
        FlowPilotNotificationListener.clearTransientState()
    }

    @Test
    fun smsRejectsDisabledIntakeAndStaleEventsAtDrain() {
        assertThat(SmsEventTracker.enqueueIfEnabled(false, "one", "body", now)).isFalse()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "one", "body", now - SmsEventTracker.MAX_EVENT_AGE_MS - 1)).isTrue()

        assertThat(SmsEventTracker.drainEvents(now)).isEmpty()
    }

    @Test
    fun smsQueueIsBoundedFifoAndDedupeRetentionIsBounded() {
        repeat(SmsEventTracker.MAX_QUEUE_SIZE + 1) { index ->
            assertThat(SmsEventTracker.enqueueIfEnabled(true, "sender-$index", "body", now)).isEqualTo(index < SmsEventTracker.MAX_QUEUE_SIZE)
        }
        assertThat(SmsEventTracker.drainEvents(now).map { it.sender })
            .containsExactlyElementsIn((0 until SmsEventTracker.MAX_QUEUE_SIZE).map { "sender-$it" }).inOrder()

        SmsEventTracker.clear()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "oldest", "body", now)).isTrue()
        SmsEventTracker.drainEvents(now)
        repeat(SmsEventTracker.MAX_DEDUPE_ENTRIES) { index ->
            assertThat(SmsEventTracker.enqueueIfEnabled(true, "dedupe-$index", "body", now)).isTrue()
            SmsEventTracker.drainEvents(now)
        }
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "oldest", "body", now)).isTrue()
    }

    @Test
    fun smsClearResetsQueueAndDedupe() {
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "sender", "body", now)).isTrue()
        SmsEventTracker.clear()

        assertThat(SmsEventTracker.drainEvents(now)).isEmpty()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "sender", "body", now)).isTrue()
    }

    @Test
    fun notificationRejectsDisabledIntakeAndStaleEventsAtDrain() {
        val event = notification("disabled", now)
        assertThat(FlowPilotNotificationListener.enqueueIfEnabled(false, event)).isFalse()
        assertThat(FlowPilotNotificationListener.enqueueIfEnabled(true, notification("stale", now - FlowPilotNotificationListener.MAX_EVENT_AGE_MS - 1))).isTrue()

        assertThat(FlowPilotNotificationListener.drainEvents(now)).isEmpty()
    }

    @Test
    fun notificationQueueIsBoundedFifoAndClearResetsDedupe() {
        repeat(FlowPilotNotificationListener.MAX_QUEUE_SIZE + 1) { index ->
            assertThat(FlowPilotNotificationListener.enqueueIfEnabled(true, notification("$index", now))).isEqualTo(index < FlowPilotNotificationListener.MAX_QUEUE_SIZE)
        }
        assertThat(FlowPilotNotificationListener.drainEvents(now).map { it.key })
            .containsExactlyElementsIn((0 until FlowPilotNotificationListener.MAX_QUEUE_SIZE).map { "key-$it" }).inOrder()

        val replay = notification("replay", now)
        assertThat(FlowPilotNotificationListener.enqueueIfEnabled(true, replay)).isTrue()
        FlowPilotNotificationListener.clearTransientState()
        assertThat(FlowPilotNotificationListener.enqueueIfEnabled(true, replay)).isTrue()
    }

    @Test
    fun notificationDedupeRetentionIsBounded() {
        val dedupe = NotificationDeduplicator(ttlMs = Long.MAX_VALUE, maxEntries = 2)

        assertThat(dedupe.shouldProcess("first", 1, now)).isTrue()
        assertThat(dedupe.shouldProcess("second", 1, now)).isTrue()
        assertThat(dedupe.shouldProcess("third", 1, now)).isTrue()
        assertThat(dedupe.shouldProcess("first", 1, now)).isTrue()
    }

    @Test
    fun executionAuthorizationRejectsDrainedBatchAfterDisable() {
        val authorization = EventExecutionAuthorization()
        val token = authorization.authorize(engineEnabled = true)

        assertThat(token).isNotNull()
        assertThat(authorization.isAuthorized(token!!)).isTrue()
        assertThat(authorization.executeIfAuthorized(token) { "ran" }).isEqualTo("ran")
        authorization.invalidate()
        assertThat(authorization.isAuthorized(token)).isFalse()
        assertThat(authorization.executeIfAuthorized(token) { "ran" }).isNull()
        assertThat(authorization.authorize(engineEnabled = false)).isNull()
    }

    private fun notification(id: String, postTime: Long) = TransientNotificationEvent(
        packageName = "com.example.$id",
        postTime = postTime,
        key = "key-$id",
        title = "title",
        text = "text",
    )
}
