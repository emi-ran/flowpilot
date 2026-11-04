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

    @Test
    fun smsDrainWindowIsInclusiveAndDropsFutureEvents() {
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550101", "body", now)).isTrue()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550102", "body", now - SmsEventTracker.MAX_EVENT_AGE_MS)).isTrue()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550103", "body", now - SmsEventTracker.MAX_EVENT_AGE_MS - 1)).isTrue()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550104", "body", now + 1)).isTrue()

        assertThat(SmsEventTracker.drainEvents(now).map { it.sender })
            .containsExactly("+15550101", "+15550102").inOrder()
    }

    @Test
    fun smsRejectsDuplicatesWithinTtlAndNormalizesSender() {
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+1 555 0100", "body", now)).isTrue()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+1 555 0100", "body", now)).isFalse()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550100", "body", now)).isFalse()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550100", "other body", now)).isTrue()
    }

    @Test
    fun notificationDrainWindowIsInclusiveAndDropsFutureEvents() {
        assertThat(FlowPilotNotificationListener.enqueueIfEnabled(true, notification("fresh", now))).isTrue()
        assertThat(
            FlowPilotNotificationListener.enqueueIfEnabled(
                true,
                notification("edge-old", now - FlowPilotNotificationListener.MAX_EVENT_AGE_MS),
            ),
        ).isTrue()
        assertThat(
            FlowPilotNotificationListener.enqueueIfEnabled(
                true,
                notification("too-old", now - FlowPilotNotificationListener.MAX_EVENT_AGE_MS - 1),
            ),
        ).isTrue()
        assertThat(FlowPilotNotificationListener.enqueueIfEnabled(true, notification("future", now + 1))).isTrue()

        assertThat(FlowPilotNotificationListener.drainEvents(now).map { it.key })
            .containsExactly("key-fresh", "key-edge-old").inOrder()
    }

    @Test
    fun notificationRejectsDuplicatesAndOlderPostTimes() {
        val dedupe = NotificationDeduplicator(ttlMs = Long.MAX_VALUE)

        assertThat(dedupe.shouldProcess("key", 10L, now)).isTrue()
        assertThat(dedupe.shouldProcess("key", 10L, now)).isFalse()
        assertThat(dedupe.shouldProcess("key", 9L, now)).isFalse()
        assertThat(dedupe.shouldProcess("key", 11L, now)).isTrue()
    }

    @Test
    fun notificationDedupeExpiresOnlyAfterTtl() {
        val dedupe = NotificationDeduplicator(ttlMs = 1_000L)

        assertThat(dedupe.shouldProcess("key", 5L, 0L)).isTrue()
        assertThat(dedupe.shouldProcess("key", 5L, 1_000L)).isFalse()
        assertThat(dedupe.shouldProcess("key", 5L, 1_001L)).isTrue()
    }

    @Test
    fun reauthorizationInvalidatesOldTokensButAllowsNewOnes() {
        val authorization = EventExecutionAuthorization()
        val before = authorization.authorize(engineEnabled = true)!!
        authorization.invalidate()
        val after = authorization.authorize(engineEnabled = true)!!

        assertThat(authorization.isAuthorized(before)).isFalse()
        assertThat(authorization.executeIfAuthorized(before) { "ran" }).isNull()
        assertThat(authorization.isAuthorized(after)).isTrue()
        assertThat(authorization.executeIfAuthorized(after) { "ran" }).isEqualTo("ran")

        authorization.invalidate()
        assertThat(authorization.isAuthorized(after)).isFalse()
    }

    private fun notification(id: String, postTime: Long) = TransientNotificationEvent(
        packageName = "com.example.$id",
        postTime = postTime,
        key = "key-$id",
        title = "title",
        text = "text",
    )
}
