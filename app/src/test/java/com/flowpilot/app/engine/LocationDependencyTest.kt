package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationDependencyTest {
    @Test
    fun requiresLocation_onlyWhenWebhookOrSmsTemplateUsesLocationToken() {
        val base = Automation(id = "test", name = "test", createdAt = 1L)
        assertThat(base.requiresLocation()).isFalse()
        assertThat(base.copy(actions = listOf(ActionType.HTTP_WEBHOOK), webhookBody = "${'$'}{location.lat}").requiresLocation()).isTrue()
        assertThat(base.copy(actions = listOf(ActionType.SEND_SMS), smsMessage = "${'$'}{locationLat}").requiresLocation()).isTrue()
        assertThat(base.copy(actions = listOf(ActionType.SHOW_NOTIFICATION), notificationBody = "${'$'}{location.lat}").requiresLocation()).isFalse()
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
