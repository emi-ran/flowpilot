package com.flowpilot.app.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineStabilityTest {
    private val now = 1_000_000L

    @After
    fun clear() {
        SmsEventTracker.clear()
        FlowPilotNotificationListener.clearTransientState()
    }

    @Test
    fun teardownReadsDisabledStateEvenWhenJobIsCancelled() = runBlocking<Unit> {
        var disabled: Boolean? = null
        val job = launch {
            try {
                delay(60_000)
            } finally {
                disabled = readEngineDisabledForTeardown { false }
            }
        }
        yield()
        job.cancelAndJoin()

        assertThat(disabled).isTrue()
    }

    @Test
    fun teardownDefaultsToNotDisabledOnReadFailureOrTimeout() = runBlocking<Unit> {
        assertThat(readEngineDisabledForTeardown { error("boom") }).isFalse()
        assertThat(readEngineDisabledForTeardown(timeoutMs = 20) { delay(5_000); false }).isFalse()
        assertThat(readEngineDisabledForTeardown { true }).isFalse()
    }

    @Test
    fun failingPollDoesNotSkipLaterPolls() = runBlocking<Unit> {
        val ran = mutableListOf<String>()
        val failures = PollFailureTracker(threshold = 2)
        val reported = mutableListOf<Triple<String, String, Int>>()

        repeat(2) {
            runIsolatedPolls(
                polls = listOf(
                    pollStep("a") { ran += "a" },
                    pollStep("b") { error("fail") },
                    pollStep("c") { ran += "c" },
                ),
                failures = failures,
                onFailure = { name, errorClass, count -> reported += Triple(name, errorClass, count) },
            )
        }

        assertThat(ran).containsExactly("a", "c", "a", "c").inOrder()
        assertThat(reported).containsExactly(
            Triple("b", "IllegalStateException", 1),
            Triple("b", "IllegalStateException", 2),
        ).inOrder()
        assertThat(failures.reachedThreshold(1)).isFalse()
        assertThat(failures.reachedThreshold(2)).isTrue()
    }

    @Test
    fun pollSuccessResetsFailureStreak() = runBlocking<Unit> {
        val failures = PollFailureTracker(threshold = 2)
        var fail = true
        val counts = mutableListOf<Int>()
        suspend fun run() = runIsolatedPolls(
            polls = listOf(pollStep("p") { if (fail) error("x") }),
            failures = failures,
            onFailure = { _, _, count -> counts += count },
        )
        run()
        fail = false
        run()
        fail = true
        run()

        assertThat(counts).containsExactly(1, 1).inOrder()
    }

    @Test
    fun pollCancellationIsRethrownAndStopsRemainingPolls() = runBlocking<Unit> {
        val ran = mutableListOf<String>()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                runIsolatedPolls(
                    polls = listOf(
                        pollStep("a") { throw CancellationException("cancel") },
                        pollStep("b") { ran += "b" },
                    ),
                    failures = PollFailureTracker(),
                    onFailure = { _, _, _ -> ran += "reported" },
                )
            }
        }
        assertThat(ran).isEmpty()
    }

    @Test
    fun smsWithSameTimestampGetDistinctExecutionIds() {
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550101", "first", now)).isTrue()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550102", "second", now)).isTrue()

        val ids = SmsEventTracker.drainEvents(now).map { it.executionId }

        assertThat(ids).hasSize(2)
        assertThat(ids.toSet()).hasSize(2)
        ids.forEach {
            assertThat(it).doesNotContain("+1555")
            assertThat(it).doesNotContain("first")
        }
    }

    @Test
    fun staleDrainDropsEventsWithoutThrowingWhenLoggingIsUnavailable() {
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550101", "old", now - SmsEventTracker.MAX_EVENT_AGE_MS - 1)).isTrue()
        assertThat(SmsEventTracker.enqueueIfEnabled(true, "+15550102", "fresh", now)).isTrue()

        assertThat(SmsEventTracker.drainEvents(now).map { it.body }).containsExactly("fresh")
    }
}
