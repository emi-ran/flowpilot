package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EventTemplateContextTest {

    @Test
    fun chargerEventKeepsItsOwnChargingStateWhenLivePollLags() {
        val base = EventTemplateContext.charger(
            ChargerEvent.DISCONNECTED,
            LiveSystemState(isChargerConnected = true),
        )
        val context = EventTemplateContext.forRule(
            base = base,
            rule = Automation(id = "rule", name = "Rule", triggerEvent = TriggerEvent.CHARGER_DISCONNECTED, createdAt = 0L),
        )

        assertThat(context.isCharging).isFalse()
        assertThat(context.values["charger.connected"]).isNull()
    }

    @Test
    fun wifiEventKeepsItsOwnSsidWhenLivePollLags() {
        val base = EventTemplateContext.wifi(
            WifiTransition(WifiStateEvent.DISCONNECTED, ""),
            LiveSystemState(connectedWifiSsid = "Old network"),
        )
        val context = EventTemplateContext.forRule(
            base = base,
            rule = Automation(id = "rule", name = "Rule", triggerEvent = TriggerEvent.WIFI_DISCONNECTED, createdAt = 0L),
        )

        assertThat(context.wifiSsid).isEmpty()
        assertThat(context.values["wifi.ssid"]).isEmpty()
    }
}
