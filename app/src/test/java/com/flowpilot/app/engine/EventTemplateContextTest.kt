package com.flowpilot.app.engine

import com.flowpilot.app.actions.RenderMode
import com.flowpilot.app.actions.TemplateRenderer
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

    @Test
    fun smsEventExposesSenderBodyAndExtractedOtpToStrictTemplates() {
        val event = SmsReceivedEvent(
            sender = "+90 555 000 00 00",
            body = "Doğrulama kodunuz 482912 olarak alınmıştır.",
            timestamp = 1_793_708_460_000L,
        )
        val context = EventTemplateContext.forRule(
            base = EventTemplateContext.sms(event, LiveSystemState(batteryPercent = 64)),
            rule = Automation(
                id = "sms-rule",
                name = "SMS Rule",
                triggerEvent = TriggerEvent.SMS_RECEIVED,
                createdAt = 0L,
            ),
        )

        val result = TemplateRenderer.renderResult(
            "\${sms.sender}|\${sms.body}|\${sms.otp}|\${smsSender}",
            context,
            RenderMode.STRICT,
        )

        assertThat(result.unknownVariables).isEmpty()
        assertThat(result.parseErrors).isEmpty()
        assertThat(result.text).isEqualTo(
            "+90 555 000 00 00|Doğrulama kodunuz 482912 olarak alınmıştır.|482912|+90 555 000 00 00",
        )
        assertThat(context.trigger).isEqualTo(TriggerEvent.SMS_RECEIVED.name)
    }

    @Test
    fun smsEventRendersBodyButStrictlyRejectsNotificationVariables() {
        val context = EventTemplateContext.forRule(
            base = EventTemplateContext.sms(
                SmsReceivedEvent(sender = "+90 555 000 00 00", body = "Kod: 123456"),
                LiveSystemState(),
            ),
            rule = Automation(
                id = "sms-rule",
                name = "SMS Rule",
                triggerEvent = TriggerEvent.SMS_RECEIVED,
                createdAt = 0L,
            ),
        )

        val result = TemplateRenderer.renderResult(
            "\${notification.text}|\${sms.body}",
            context,
            RenderMode.STRICT,
        )

        assertThat(result.unknownVariables).containsExactly("notification.text")
        assertThat(result.parseErrors).isEmpty()
        assertThat(result.text).isEqualTo("\${notification.text}|Kod: 123456")
    }
}
