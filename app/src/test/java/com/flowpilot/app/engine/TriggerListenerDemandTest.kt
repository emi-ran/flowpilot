package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerDefinition
import com.flowpilot.app.data.model.TriggerDefinitions
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TriggerListenerDemandTest {
    private fun rule(event: TriggerEvent) = Automation(
        id = event.name, name = event.name, createdAt = 1L, triggerEvent = event,
    )

    @Test fun mediaDemand_requiresAnEnabledMediaTrigger() {
        val mediaEvents = listOf(TriggerEvent.MEDIA_PLAYING, TriggerEvent.MEDIA_PAUSED, TriggerEvent.MEDIA_TRACK_CHANGED)
        assertThat(TriggerListenerDemand.from(emptyList()).needsMedia).isFalse()
        for (event in mediaEvents) {
            val media = rule(event)
            assertThat(TriggerListenerDemand.from(listOf(media)).needsMedia).isTrue()
            assertThat(TriggerListenerDemand.from(listOf(media.copy(enabled = false))).needsMedia).isFalse()
        }
        val unrelated = listOf(rule(TriggerEvent.APP_OPENED), rule(TriggerEvent.NOTIFICATION_RECEIVED), rule(TriggerEvent.SCREEN_ON))
        assertThat(TriggerListenerDemand.from(unrelated).needsMedia).isFalse()
        val media = rule(TriggerEvent.MEDIA_PLAYING)
        assertThat(TriggerListenerDemand.from(unrelated + media).needsMedia).isTrue()
        assertThat(TriggerListenerDemand.from(unrelated + media.copy(enabled = false)).needsMedia).isFalse()
    }

    @Test fun headsetDemand_requiresAnEnabledHeadsetTrigger() {
        assertThat(TriggerListenerDemand.from(emptyList()).needsHeadset).isFalse()
        for (event in listOf(TriggerEvent.HEADSET_CONNECTED, TriggerEvent.HEADSET_DISCONNECTED)) {
            val headset = rule(event)
            assertThat(TriggerListenerDemand.from(listOf(headset)).needsHeadset).isTrue()
            assertThat(TriggerListenerDemand.from(listOf(headset.copy(enabled = false))).needsHeadset).isFalse()
        }
        val unrelated = listOf(rule(TriggerEvent.BLUETOOTH_CONNECTED), rule(TriggerEvent.CHARGER_CONNECTED), rule(TriggerEvent.MEDIA_PLAYING))
        assertThat(TriggerListenerDemand.from(unrelated).needsHeadset).isFalse()
        val headset = rule(TriggerEvent.HEADSET_CONNECTED)
        assertThat(TriggerListenerDemand.from(unrelated + headset).needsHeadset).isTrue()
        assertThat(TriggerListenerDemand.from(unrelated + headset.copy(enabled = false)).needsHeadset).isFalse()
    }

    @Test fun headsetDemand_followsEnabledMultiTriggerSourcesAndStopsWhenRemoved() {
        val multi = rule(TriggerEvent.SCREEN_ON).copy(triggerDefinitions = TriggerDefinitions(items = listOf(
            TriggerDefinition.ScreenOn, TriggerDefinition.HeadsetDisconnected,
        )))
        val demand = TriggerListenerDemand.from(listOf(multi))
        assertThat(demand.needsHeadset).isTrue()
        assertThat(demand.needs(TriggerEvent.HEADSET_DISCONNECTED)).isTrue()
        assertThat(demand.needs(TriggerEvent.HEADSET_CONNECTED)).isFalse()
        // Editing the rule so no headset source remains drops the demand (listener is unregistered).
        val edited = multi.copy(triggerDefinitions = TriggerDefinitions(items = listOf(TriggerDefinition.ScreenOn)))
        assertThat(TriggerListenerDemand.from(listOf(edited)).needsHeadset).isFalse()
        assertThat(TriggerListenerDemand.from(listOf(multi.copy(enabled = false))).needsHeadset).isFalse()
    }

    @Test fun mediaDemand_includesEnabledMultipleTriggerSourcesOnly() {
        val multi = rule(TriggerEvent.SCREEN_ON).copy(triggerDefinitions = TriggerDefinitions(items = listOf(
            TriggerDefinition.AppOpened("music.player"),
            TriggerDefinition.MediaPaused("music.player"),
            TriggerDefinition.MediaTrackChanged(),
        )))
        val demand = TriggerListenerDemand.from(listOf(multi))
        assertThat(demand.needsMedia).isTrue()
        assertThat(demand.needs(TriggerEvent.MEDIA_PAUSED)).isTrue()
        assertThat(demand.needs(TriggerEvent.MEDIA_TRACK_CHANGED)).isTrue()
        assertThat(demand.needs(TriggerEvent.MEDIA_PLAYING)).isFalse()
        assertThat(demand.appPackages).containsExactly("music.player")

        val disabled = TriggerListenerDemand.from(listOf(multi.copy(enabled = false), rule(TriggerEvent.SCREEN_ON)))
        assertThat(disabled.needsMedia).isFalse()
        assertThat(disabled.needs(TriggerEvent.SCREEN_ON)).isTrue()
        assertThat(disabled.appPackages).isEmpty()
    }
}
