package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GeofenceRetryPolicyTest {

    private fun createRule(
        id: String = UUID.randomUUID().toString(),
        name: String = "Geofence Rule",
        trigger: TriggerEvent = TriggerEvent.GEOFENCE_ENTER,
        enabled: Boolean = true,
        lat: Double = 41.0082,
        lng: Double = 28.9784,
        radius: Int = 150,
    ) = Automation(
        id = id,
        name = name,
        enabled = enabled,
        triggerEvent = trigger,
        geofenceLatitude = lat,
        geofenceLongitude = lng,
        geofenceRadiusMeters = radius,
        createdAt = System.currentTimeMillis(),
    )

    @Test
    fun initialAttempt_shouldAttemptIsTrue() {
        val policy = GeofenceRetryPolicy(baseBackoffMs = 10_000L, maxBackoffMs = 60_000L)
        val rule = createRule(id = "rule-1")
        val readyPrereqs = GeofencePrerequisitesResult.Ready(isFine = true)

        val shouldAttempt = policy.shouldAttemptRegistration(
            rulesToAdd = listOf(rule),
            prereqResult = readyPrereqs,
            now = 1_000L,
        )
        assertTrue(shouldAttempt)
    }

    @Test
    fun repeatedTick_duringCooldown_shouldAttemptIsFalse() {
        val policy = GeofenceRetryPolicy(baseBackoffMs = 10_000L, maxBackoffMs = 60_000L)
        val rule = createRule(id = "rule-1")
        val missingPermission = GeofencePrerequisitesResult.MissingLocationPermission("Precise location permission is required.")

        // First attempt allowed
        assertTrue(policy.shouldAttemptRegistration(listOf(rule), missingPermission, now = 1_000L))

        // Record failure at 1_000L
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = missingPermission.message, now = 1_000L)

        // 500ms later (next engine tick at 1_500L), same config and same error
        val shouldAttemptNextTick = policy.shouldAttemptRegistration(
            rulesToAdd = listOf(rule),
            prereqResult = missingPermission,
            now = 1_500L,
        )
        assertFalse("Subsequent poll tick within cooldown must not attempt registration", shouldAttemptNextTick)

        // 5 seconds later (6_000L), still in cooldown
        assertFalse(policy.shouldAttemptRegistration(listOf(rule), missingPermission, now = 6_000L))
    }

    @Test
    fun afterBackoffDuration_shouldAttemptIsTrue() {
        val policy = GeofenceRetryPolicy(baseBackoffMs = 10_000L, maxBackoffMs = 60_000L)
        val rule = createRule(id = "rule-1")
        val missingPermission = GeofencePrerequisitesResult.MissingLocationPermission("Precise location permission is required.")

        policy.shouldAttemptRegistration(listOf(rule), missingPermission, now = 1_000L)
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = missingPermission.message, now = 1_000L)

        // After baseBackoffMs (at 11_000L)
        val shouldAttemptAfterCooldown = policy.shouldAttemptRegistration(
            rulesToAdd = listOf(rule),
            prereqResult = missingPermission,
            now = 11_000L,
        )
        assertTrue("After cooldown backoff has elapsed, retry must be allowed", shouldAttemptAfterCooldown)
    }

    @Test
    fun exponentialBackoff_capsAtMaxBackoff() {
        val policy = GeofenceRetryPolicy(baseBackoffMs = 10_000L, maxBackoffMs = 60_000L)
        val rule = createRule(id = "rule-1")
        val error = "GPS registration error"

        // 1st failure: 10s
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = error, now = 1000L)
        assertEquals(11_000L, policy.getFailureRecord("rule-1")?.nextRetryAt)

        // 2nd failure: 20s
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = error, now = 11_000L)
        assertEquals(31_000L, policy.getFailureRecord("rule-1")?.nextRetryAt)

        // 3rd failure: 40s
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = error, now = 31_000L)
        assertEquals(71_000L, policy.getFailureRecord("rule-1")?.nextRetryAt)

        // 4th failure: capped at max 60s
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = error, now = 71_000L)
        assertEquals(131_000L, policy.getFailureRecord("rule-1")?.nextRetryAt)

        // 5th failure: still capped at 60s
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = error, now = 131_000L)
        assertEquals(191_000L, policy.getFailureRecord("rule-1")?.nextRetryAt)
    }

    @Test
    fun prerequisitesChanged_immediatelyAttemptsWithoutWaitingForCooldown() {
        val policy = GeofenceRetryPolicy(baseBackoffMs = 10_000L, maxBackoffMs = 60_000L)
        val rule = createRule(id = "rule-1")
        val missingPermission = GeofencePrerequisitesResult.MissingLocationPermission("Precise location permission is required.")

        policy.shouldAttemptRegistration(listOf(rule), missingPermission, now = 1_000L)
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = missingPermission.message, now = 1_000L)

        // At 2_000L (well within 10s cooldown), user grants permission!
        val readyPrereqs = GeofencePrerequisitesResult.Ready(isFine = true)
        val shouldAttempt = policy.shouldAttemptRegistration(
            rulesToAdd = listOf(rule),
            prereqResult = readyPrereqs,
            now = 2_000L,
        )
        assertTrue("Prerequisites change must bypass backoff and attempt immediately", shouldAttempt)
    }

    @Test
    fun desiredSetChanged_ruleAddedOrRemoved_immediatelyAttempts() {
        val policy = GeofenceRetryPolicy(baseBackoffMs = 10_000L, maxBackoffMs = 60_000L)
        val rule1 = createRule(id = "rule-1")
        val rule2 = createRule(id = "rule-2")
        val disabled = GeofencePrerequisitesResult.LocationServicesDisabled("Location disabled")

        policy.shouldAttemptRegistration(listOf(rule1), disabled, now = 1_000L)
        policy.recordFailure(listOf("rule-1"), listOf(rule1), error = disabled.message, now = 1_000L)

        // User adds rule2 while in cooldown
        val shouldAttempt = policy.shouldAttemptRegistration(
            rulesToAdd = listOf(rule1, rule2),
            prereqResult = disabled,
            now = 2_000L,
        )
        assertTrue("Desired geofence set change must bypass backoff and reconcile immediately", shouldAttempt)
    }

    @Test
    fun ruleConfigChanged_coordinatesOrRadiusModified_immediatelyAttempts() {
        val policy = GeofenceRetryPolicy(baseBackoffMs = 10_000L, maxBackoffMs = 60_000L)
        val rule = createRule(id = "rule-1", radius = 150)
        val disabled = GeofencePrerequisitesResult.LocationServicesDisabled("Location disabled")

        policy.shouldAttemptRegistration(listOf(rule), disabled, now = 1_000L)
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = disabled.message, now = 1_000L)

        // User modifies radius of rule from 150 to 300
        val modifiedRule = rule.copy(geofenceRadiusMeters = 300)
        val shouldAttempt = policy.shouldAttemptRegistration(
            rulesToAdd = listOf(modifiedRule),
            prereqResult = disabled,
            now = 2_000L,
        )
        assertTrue("Rule config change must bypass backoff and attempt immediately", shouldAttempt)
    }

    @Test
    fun success_clearsFailureRecord() {
        val policy = GeofenceRetryPolicy()
        val rule = createRule(id = "rule-1")
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = "Error", now = 1_000L)
        assertNotNull(policy.getFailureRecord("rule-1"))

        policy.recordSuccess(listOf("rule-1"))
        assertNull(policy.getFailureRecord("rule-1"))
    }

    @Test
    fun unregistration_clearsFailureRecord() {
        val policy = GeofenceRetryPolicy()
        val rule = createRule(id = "rule-1")
        policy.recordFailure(listOf("rule-1"), listOf(rule), error = "Error", now = 1_000L)
        assertNotNull(policy.getFailureRecord("rule-1"))

        policy.clearRules(listOf("rule-1"))
        assertNull(policy.getFailureRecord("rule-1"))
    }

    @Test
    fun modifiedRule_reAddFollowsRetryPolicyAndBackoff() {
        val policy = GeofenceRetryPolicy(baseBackoffMs = 10_000L, maxBackoffMs = 60_000L)
        val modifiedRule = createRule(id = "rule-1", radius = 300)
        val disabled = GeofencePrerequisitesResult.LocationServicesDisabled("Location disabled")

        // 1. First attempt to re-add modified rule is allowed
        assertTrue(policy.shouldAttemptRegistration(listOf(modifiedRule), disabled, now = 1_000L))

        // 2. Re-add fails during unregister-add flow; failure is recorded
        policy.recordFailure(listOf("rule-1"), listOf(modifiedRule), error = disabled.message, now = 1_000L)

        // 3. Next tick 500ms later (whether evaluated as toReAdd or purelyNew): cooldown must be active
        assertFalse(
            "Modified rule in cooldown must not re-attempt registration every tick",
            policy.shouldAttemptRegistration(listOf(modifiedRule), disabled, now = 1_500L),
        )

        // 4. Purely removed IDs cleanup must NOT wipe modified rule's cooldown
        policy.clearRules(listOf("other-rule-deleted"))
        assertFalse(
            "Purely removed rule cleanup must not clear modified rule's cooldown",
            policy.shouldAttemptRegistration(listOf(modifiedRule), disabled, now = 1_500L),
        )

        // 5. After backoff duration (10s), retry is allowed
        assertTrue(
            "After backoff expires, modified rule must retry",
            policy.shouldAttemptRegistration(listOf(modifiedRule), disabled, now = 11_000L),
        )
    }

    @Test
    fun updateDesiredState_detectsChangesAcrossDesiredSet() {
        val policy = GeofenceRetryPolicy()
        val rule1 = createRule(id = "rule-1", radius = 150)
        val ready = GeofencePrerequisitesResult.Ready(isFine = true)

        // Initial state load
        policy.updateDesiredState(listOf(rule1), ready)

        // Same state next tick
        assertFalse(policy.updateDesiredState(listOf(rule1), ready))

        // Rule modified
        val rule1Modified = rule1.copy(geofenceRadiusMeters = 300)
        assertTrue(policy.updateDesiredState(listOf(rule1Modified), ready))

        // Same state
        assertFalse(policy.updateDesiredState(listOf(rule1Modified), ready))

        // Prerequisite changed
        val disabled = GeofencePrerequisitesResult.LocationServicesDisabled("Location disabled")
        assertTrue(policy.updateDesiredState(listOf(rule1Modified), disabled))
    }
}
