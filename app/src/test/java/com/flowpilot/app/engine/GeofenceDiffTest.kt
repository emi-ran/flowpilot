package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GeofenceDiffTest {

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
    fun calculateGeofenceDiff_addNewRules() {
        val current = emptyMap<String, Automation>()
        val desired = listOf(
            createRule(id = "rule-1", trigger = TriggerEvent.GEOFENCE_ENTER),
            createRule(id = "rule-2", trigger = TriggerEvent.GEOFENCE_EXIT),
        )

        val diff = calculateGeofenceDiff(current, desired)
        assertEquals(2, diff.toAdd.size)
        assertTrue(diff.toRemoveIds.isEmpty())
        assertEquals(setOf("rule-1", "rule-2"), diff.toAdd.map { it.id }.toSet())
    }

    @Test
    fun calculateGeofenceDiff_removeDeletedOrDisabledRules() {
        val rule1 = createRule(id = "rule-1")
        val rule2 = createRule(id = "rule-2")
        val current = mapOf("rule-1" to rule1, "rule-2" to rule2)

        // rule-1 deleted, rule-2 disabled
        val desired = listOf(
            rule2.copy(enabled = false),
        )

        val diff = calculateGeofenceDiff(current, desired)
        assertTrue(diff.toAdd.isEmpty())
        assertEquals(2, diff.toRemoveIds.size)
        assertTrue(diff.toRemoveIds.contains("rule-1"))
        assertTrue(diff.toRemoveIds.contains("rule-2"))
    }

    @Test
    fun calculateGeofenceDiff_modifiedRuleTriggersUnregisterAndRegister() {
        val original = createRule(id = "rule-1", lat = 41.0082, lng = 28.9784, radius = 150)
        val current = mapOf("rule-1" to original)

        val updated = original.copy(geofenceLatitude = 41.0100, geofenceRadiusMeters = 250)
        val desired = listOf(updated)

        val diff = calculateGeofenceDiff(current, desired)
        assertEquals(listOf("rule-1"), diff.toRemoveIds)
        assertEquals(1, diff.toAdd.size)
        assertEquals("rule-1", diff.toAdd[0].id)
        assertEquals(41.0100, diff.toAdd[0].geofenceLatitude, 0.0001)
        assertEquals(250, diff.toAdd[0].geofenceRadiusMeters)
    }

    @Test
    fun calculateGeofenceDiff_unchangedRulesProduceNoDiff() {
        val rule1 = createRule(id = "rule-1")
        val current = mapOf("rule-1" to rule1)
        val desired = listOf(rule1)

        val diff = calculateGeofenceDiff(current, desired)
        assertTrue(diff.toAdd.isEmpty())
        assertTrue(diff.toRemoveIds.isEmpty())
    }

    @Test
    fun calculateGeofenceDiff_deduplicatesDuplicateRuleIds() {
        val current = emptyMap<String, Automation>()
        val ruleA = createRule(id = "dup-id", name = "First Instance")
        val ruleB = createRule(id = "dup-id", name = "Second Instance")

        val diff = calculateGeofenceDiff(current, listOf(ruleA, ruleB))
        assertEquals(1, diff.toAdd.size)
        assertEquals("dup-id", diff.toAdd[0].id)
    }

    @Test
    fun calculateGeofenceDiff_filtersInvalidOrNonGeofenceRules() {
        val current = emptyMap<String, Automation>()
        val invalidRule = createRule(id = "invalid", lat = 0.0, lng = 0.0) // uninitialized
        val smsRule = createRule(id = "sms", trigger = TriggerEvent.SMS_RECEIVED)
        val validGeofence = createRule(id = "valid", trigger = TriggerEvent.GEOFENCE_ENTER)

        val diff = calculateGeofenceDiff(current, listOf(invalidRule, smsRule, validGeofence))
        assertEquals(1, diff.toAdd.size)
        assertEquals("valid", diff.toAdd[0].id)
    }

    @Test
    fun calculateGeofenceDiff_restoredFromPersistence_detectsStaleRuleWhenDisabledOrDeleted() {
        // Simulates process restart where registered geofences were restored from SharedPreferences
        val rule1 = createRule(id = "rule-1")
        val rule2 = createRule(id = "rule-2")
        val restoredFromPersistence = mapOf("rule-1" to rule1, "rule-2" to rule2)

        // After restart, user disabled rule-2 and added new rule-3
        val rule3 = createRule(id = "rule-3")
        val desired = listOf(
            rule1,
            rule2.copy(enabled = false),
            rule3,
        )

        val diff = calculateGeofenceDiff(restoredFromPersistence, desired)
        assertEquals(listOf("rule-2"), diff.toRemoveIds)
        assertEquals(1, diff.toAdd.size)
        assertEquals("rule-3", diff.toAdd[0].id)
    }

    @Test
    fun calculateGeofenceDiff_partitionReAddAndPurelyNewRules_preventsIdCollision() {
        // rule-1 modified, rule-2 deleted, rule-3 newly added
        val original1 = createRule(id = "rule-1", radius = 100)
        val original2 = createRule(id = "rule-2")
        val current = mapOf("rule-1" to original1, "rule-2" to original2)

        val updated1 = original1.copy(geofenceRadiusMeters = 300)
        val new3 = createRule(id = "rule-3")
        val desired = listOf(updated1, new3)

        val diff = calculateGeofenceDiff(current, desired)
        assertEquals(setOf("rule-1", "rule-2"), diff.toRemoveIds.toSet())
        assertEquals(setOf("rule-1", "rule-3"), diff.toAdd.map { it.id }.toSet())

        // Validate segregation: rule-1 must await remove before add, rule-3 is purely new
        val (toReAdd, purelyNew) = diff.toAdd.partition { it.id in diff.toRemoveIds }
        assertEquals(listOf("rule-1"), toReAdd.map { it.id })
        assertEquals(listOf("rule-3"), purelyNew.map { it.id })
    }
}
