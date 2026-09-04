package com.flowpilot.app.data.model

import org.junit.Assert.*
import org.junit.Test

class AutomationPresetsTest {

    @Test
    fun `all presets have unique ids and valid templates`() {
        val presets = AutomationPresets.all
        assertEquals(9, presets.size)

        val uniqueIds = presets.map { it.id }.toSet()
        assertEquals(presets.size, uniqueIds.size)

        for (preset in presets) {
            val template = preset.template
            assertTrue("Title should not be blank", preset.title.isNotBlank())
            assertTrue("Description should not be blank", preset.description.isNotBlank())
            assertTrue("Name should not be blank", template.name.isNotBlank())
            assertTrue("Actions should not be empty", template.effectiveActions.isNotEmpty())
            assertEquals(
                "Action delays size must match effective actions size for preset ${preset.id}",
                template.effectiveActions.size,
                template.effectiveActionDelays.size,
            )
        }
    }

    @Test
    fun `sms location responder preset opens gps and delays sms by 5 seconds`() {
        val preset = AutomationPresets.all.first { it.id == "preset_sms_location_responder" }
        val template = preset.template

        assertEquals(TriggerEvent.SMS_RECEIVED, template.triggerEvent)
        assertEquals("NEREDESIN", template.smsKeyword)
        assertEquals(SmsMatchMode.CONTAINS, template.smsMatchMode)

        // Action 1: LOCATION_ON (delay 0)
        // Action 2: SEND_SMS (delay 5s)
        assertEquals(2, template.effectiveActions.size)
        assertEquals(ActionType.LOCATION_ON, template.effectiveActions[0])
        assertEquals(0, template.effectiveActionDelays[0])

        assertEquals(ActionType.SEND_SMS, template.effectiveActions[1])
        assertEquals(5, template.effectiveActionDelays[1])

        assertTrue(template.smsRecipient.contains("\${sms.sender}"))
        assertTrue(template.smsMessage.contains("\${location.maps_url}"))
        assertTrue(template.smsMessage.contains("\${batteryPercent}"))
    }

    @Test
    fun `bedtime routine preset configures night actions and time conditions`() {
        val preset = AutomationPresets.all.first { it.id == "preset_bedtime" }
        val template = preset.template

        assertEquals(TriggerEvent.TIME_SCHEDULE, template.triggerEvent)
        assertEquals(23 * 60 + 30, template.scheduledMinute)
        assertEquals(1, template.conditions.size)
        assertEquals(ConditionType.TIME_BETWEEN, template.conditions[0].type)
        assertTrue(template.effectiveActions.contains(ActionType.DARK_THEME_ON))
        assertTrue(template.effectiveActions.contains(ActionType.DND_ON))
        assertTrue(template.effectiveActions.contains(ActionType.SOUND_PROFILE_SILENT))
        assertTrue(template.effectiveActions.contains(ActionType.SET_SCREEN_BRIGHTNESS))
        assertEquals(10, template.screenBrightnessPercent)
    }

    @Test
    fun `full battery alert preset requires charger connected and speaks alert`() {
        val preset = AutomationPresets.all.first { it.id == "preset_full_battery" }
        val template = preset.template

        assertEquals(TriggerEvent.BATTERY_ABOVE, template.triggerEvent)
        assertEquals(99, template.batteryLevel)
        assertEquals(1, template.conditions.size)
        assertEquals(ConditionType.CHARGER_CONNECTED, template.conditions[0].type)
        assertTrue(template.effectiveActions.contains(ActionType.SPEAK_TEXT))
        assertTrue(template.ttsText.isNotBlank())
    }

    @Test
    fun `motion presets configure correct triggers and vibrations`() {
        val flip = AutomationPresets.all.first { it.id == "preset_flip_silence" }
        assertEquals(TriggerEvent.DEVICE_FLIPPED_DOWN, flip.template.triggerEvent)
        assertTrue(flip.template.effectiveActions.contains(ActionType.DND_ON))
        assertTrue(flip.template.effectiveActions.contains(ActionType.VIBRATE))

        val shake = AutomationPresets.all.first { it.id == "preset_shake_torch" }
        assertEquals(TriggerEvent.DEVICE_SHAKE, shake.template.triggerEvent)
        assertTrue(shake.template.effectiveActions.contains(ActionType.TORCH_ON))
        assertTrue(shake.template.effectiveActions.contains(ActionType.VIBRATE))
    }
}
