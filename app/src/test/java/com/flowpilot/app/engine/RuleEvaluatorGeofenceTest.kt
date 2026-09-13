package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.TriggerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RuleEvaluatorGeofenceTest {

    private fun createGeofenceRule(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Geofence Rule",
        trigger: TriggerEvent = TriggerEvent.GEOFENCE_ENTER,
        enabled: Boolean = true,
        cooldownMinutes: Int = 0,
        lastTriggeredAt: Long = 0L,
        conditions: List<RuleCondition> = emptyList(),
        geofenceName: String = "Home",
        geofenceLat: Double = 41.0082,
        geofenceLng: Double = 28.9784,
        geofenceRadius: Int = 150,
    ) = Automation(
        id = id,
        name = name,
        enabled = enabled,
        triggerEvent = trigger,
        cooldownMinutes = cooldownMinutes,
        lastTriggeredAt = lastTriggeredAt,
        conditions = conditions,
        geofenceName = geofenceName,
        geofenceLatitude = geofenceLat,
        geofenceLongitude = geofenceLng,
        geofenceRadiusMeters = geofenceRadius,
        createdAt = System.currentTimeMillis(),
    )

    @Test
    fun evaluateGeofence_matchesEnterEventCorrectly() {
        val rule1 = createGeofenceRule(id = "rule-1", trigger = TriggerEvent.GEOFENCE_ENTER)
        val rule2 = createGeofenceRule(id = "rule-2", trigger = TriggerEvent.GEOFENCE_EXIT)

        val transition = GeofenceTransition(
            automationId = "rule-1",
            event = GeofenceEvent.ENTER,
            currentLat = 41.0082,
            currentLng = 28.9784,
            distanceMeters = 20f,
            targetLat = 41.0082,
            targetLng = 28.9784,
            radiusMeters = 150,
        )

        val matched = RuleEvaluator.evaluateGeofence(listOf(rule1, rule2), transition)
        assertEquals(1, matched.size)
        assertEquals("rule-1", matched[0].id)
    }

    @Test
    fun evaluateGeofence_matchesExitEventCorrectly() {
        val rule1 = createGeofenceRule(id = "rule-1", trigger = TriggerEvent.GEOFENCE_ENTER)
        val rule2 = createGeofenceRule(id = "rule-2", trigger = TriggerEvent.GEOFENCE_EXIT)

        val transition = GeofenceTransition(
            automationId = "rule-2",
            event = GeofenceEvent.EXIT,
            currentLat = 41.0150,
            currentLng = 28.9800,
            distanceMeters = 800f,
            targetLat = 41.0082,
            targetLng = 28.9784,
            radiusMeters = 150,
        )

        val matched = RuleEvaluator.evaluateGeofence(listOf(rule1, rule2), transition)
        assertEquals(1, matched.size)
        assertEquals("rule-2", matched[0].id)
    }

    @Test
    fun evaluateGeofence_observedOppositeTransitionDoesNotExecuteRule() {
        val rule = createGeofenceRule(id = "rule-1", trigger = TriggerEvent.GEOFENCE_ENTER)
        val exitTransition = GeofenceTransition(automationId = "rule-1", event = GeofenceEvent.EXIT)

        val matched = RuleEvaluator.evaluateGeofence(listOf(rule), exitTransition)

        assertTrue("Both transitions may be registered, but only configured trigger may execute", matched.isEmpty())
    }

    @Test
    fun evaluateGeofence_disabledRuleIsSuppressed() {
        val rule = createGeofenceRule(id = "rule-1", trigger = TriggerEvent.GEOFENCE_ENTER, enabled = false)
        val transition = GeofenceTransition(
            automationId = "rule-1",
            event = GeofenceEvent.ENTER,
            currentLat = 41.0082,
            currentLng = 28.9784,
            distanceMeters = 20f,
            targetLat = 41.0082,
            targetLng = 28.9784,
            radiusMeters = 150,
        )
        val matched = RuleEvaluator.evaluateGeofence(listOf(rule), transition)
        assertTrue(matched.isEmpty())
    }

    @Test
    fun evaluateGeofence_cooldownSuppressesExecution() {
        val now = 100_000L
        val rule = createGeofenceRule(
            id = "rule-1",
            trigger = TriggerEvent.GEOFENCE_ENTER,
            cooldownMinutes = 5,
            lastTriggeredAt = now - 60_000L, // 1 minute ago, cooldown is 5m
        )
        val transition = GeofenceTransition(
            automationId = "rule-1",
            event = GeofenceEvent.ENTER,
            currentLat = 41.0082,
            currentLng = 28.9784,
            distanceMeters = 20f,
            targetLat = 41.0082,
            targetLng = 28.9784,
            radiusMeters = 150,
        )
        val matched = RuleEvaluator.evaluateGeofence(listOf(rule), transition, nowMs = now)
        assertTrue("Cooldown must suppress execution", matched.isEmpty())
    }

    @Test
    fun evaluateGeofence_respectsConditions() {
        val now = 100_000L
        val ruleWithWifiCondition = createGeofenceRule(
            id = "rule-1",
            trigger = TriggerEvent.GEOFENCE_ENTER,
            conditions = listOf(
                RuleCondition(type = ConditionType.WIFI_CONNECTED, wifiSsid = "Home_Wifi")
            ),
        )
        val transition = GeofenceTransition(
            automationId = "rule-1",
            event = GeofenceEvent.ENTER,
            currentLat = 41.0082,
            currentLng = 28.9784,
            distanceMeters = 20f,
            targetLat = 41.0082,
            targetLng = 28.9784,
            radiusMeters = 150,
        )

        // When Wi-Fi is NOT connected:
        val matchedDisconnected = RuleEvaluator.evaluateGeofence(
            listOf(ruleWithWifiCondition),
            transition,
            liveState = LiveSystemState(connectedWifiSsid = null),
            nowMs = now,
        )
        assertTrue("Condition not met; should not execute", matchedDisconnected.isEmpty())

        // When Wi-Fi IS connected:
        val matchedConnected = RuleEvaluator.evaluateGeofence(
            listOf(ruleWithWifiCondition),
            transition,
            liveState = LiveSystemState(connectedWifiSsid = "Home_Wifi"),
            nowMs = now,
        )
        assertEquals(1, matchedConnected.size)
    }
}
