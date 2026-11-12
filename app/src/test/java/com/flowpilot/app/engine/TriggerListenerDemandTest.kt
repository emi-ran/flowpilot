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
