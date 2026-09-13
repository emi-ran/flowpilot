package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationDependencyTest {
    @Test
    fun requiresLocation_onlyWhenWebhookOrSmsTemplateUsesLocationToken() {
        val base = Automation(id = "test", name = "test", createdAt = 1L)
        assertThat(base.requiresLocation()).isFalse()
        assertThat(base.copy(actions = listOf(ActionType.HTTP_WEBHOOK), webhookBody = "${'$'}{location.lat}").requiresLocation()).isTrue()
        assertThat(base.copy(actions = listOf(ActionType.SEND_SMS), smsMessage = "${'$'}{locationLat}").requiresLocation()).isTrue()
        assertThat(base.copy(actions = listOf(ActionType.SHOW_NOTIFICATION), notificationBody = "${'$'}{location.lat}").requiresLocation()).isFalse()
        assertThat(base.copy(triggerEvent = TriggerEvent.GEOFENCE_ENTER, actions = listOf(ActionType.SHOW_NOTIFICATION)).requiresLocation()).isFalse()
        assertThat(base.copy(triggerEvent = TriggerEvent.GEOFENCE_EXIT, actions = listOf(ActionType.SHOW_NOTIFICATION)).requiresLocation()).isFalse()
        assertThat(base.copy(triggerEvent = TriggerEvent.GEOFENCE_ENTER, actions = listOf(ActionType.HTTP_WEBHOOK), webhookBody = "${'$'}{location.lat}").requiresLocation()).isTrue()
        assertThat(base.copy(triggerEvent = TriggerEvent.GEOFENCE_EXIT, actions = listOf(ActionType.SEND_SMS), smsMessage = "${'$'}{locationLng}").requiresLocation()).isTrue()
    }

    @Test
    fun resolveExecutionCoordinates_usesEventCoordinatesWithoutFreshLocationProvider() = runBlocking {
        var providerCalled = false
        val coords = resolveExecutionCoordinates(
            requiresLocation = true,
            eventCoordinates = 41.0082 to 28.9784,
            freshLocationProvider = {
                providerCalled = true
                0.0 to 0.0
            },
        )
        assertThat(coords).isEqualTo(41.0082 to 28.9784)
        assertThat(providerCalled).isFalse()
    }

    @Test
    fun resolveExecutionCoordinates_skipsProviderWhenLocationNotRequired() = runBlocking {
        var providerCalled = false
        val coords = resolveExecutionCoordinates(
            requiresLocation = false,
            eventCoordinates = null,
            freshLocationProvider = {
                providerCalled = true
                41.0082 to 28.9784
            },
        )
        assertThat(coords).isNull()
        assertThat(providerCalled).isFalse()
    }

    @Test
    fun resolveExecutionCoordinates_fallsBackToFreshProviderWhenEventCoordinatesMissing() = runBlocking {
        var providerCalled = false
        val coords = resolveExecutionCoordinates(
            requiresLocation = true,
            eventCoordinates = null,
            freshLocationProvider = {
                providerCalled = true
                41.0082 to 28.9784
            },
        )
        assertThat(coords).isEqualTo(41.0082 to 28.9784)
        assertThat(providerCalled).isTrue()
    }

    @Test
    fun geofenceTransition_coordinatesExtractsDeliveredOrTargetCoordinates() {
        val withCurrent = GeofenceTransition(
            automationId = "test",
            event = GeofenceEvent.ENTER,
            currentLat = 41.0082,
            currentLng = 28.9784,
        )
        assertThat(withCurrent.coordinates).isEqualTo(41.0082 to 28.9784)

        val withTargetFallback = GeofenceTransition(
            automationId = "test",
            event = GeofenceEvent.ENTER,
            currentLat = 0.0,
            currentLng = 0.0,
            targetLat = 41.0082,
            targetLng = 28.9784,
        )
        assertThat(withTargetFallback.coordinates).isEqualTo(41.0082 to 28.9784)

        val withZeros = GeofenceTransition(
            automationId = "test",
            event = GeofenceEvent.ENTER,
            currentLat = 0.0,
            currentLng = 0.0,
        )
        assertThat(withZeros.coordinates).isNull()
    }

    @Test
    fun locationFetcher_returnsNullBeforeAccessingLocationManager_withoutBackgroundPermission() = runBlocking {
        val app = RuntimeEnvironment.getApplication()

        val coordinates = LocationFetcher.getCoordinates(app, isBackgroundExecution = true)

        assertThat(coordinates).isNull()
    }

    @Test
    fun cachedLocation_requiresFreshTimestampAndAccuracy() {
        val location = android.location.Location("gps").apply {
            time = 90_000L
            accuracy = 50f
        }
        assertThat(isValidCachedLocation(location, 149_999L)).isTrue()
        assertThat(isValidCachedLocation(location, 150_000L)).isFalse()
        location.accuracy = 51f
        assertThat(isValidCachedLocation(location, 100_000L)).isFalse()
    }
}
