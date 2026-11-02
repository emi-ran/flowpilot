package com.flowpilot.app.data.model

import org.junit.Assert.*
import org.junit.Test

class AutomationPresetsTest {

    @Test
    fun `all presets have unique ids and valid templates`() {
        val presets = AutomationPresets.all
        assertEquals(14, presets.size)

        val uniqueIds = presets.map { it.id }.toSet()
        assertEquals(presets.size, uniqueIds.size)

        for (preset in presets) {
            val template = preset.template
            assertTrue("Title should not be blank", preset.title.isNotBlank())
            assertTrue("Description should not be blank", preset.description.isNotBlank())
            assertTrue("Title resource should be valid", preset.titleRes != 0)
            assertTrue("Description resource should be valid", preset.descriptionRes != 0)
            assertTrue("Name should not be blank", template.name.isNotBlank())
            assertTrue("Actions should not be empty", template.effectiveActions.isNotEmpty())
            assertEquals(
                "Action delays size must match effective actions size for preset ${preset.id}",
                template.effectiveActions.size,
                template.effectiveActionDelays.size,
            )
            val tree = template.conditionTree
            // The battery preset intentionally keeps flat conditions empty so readers that
            // ignore the tree fall back to the old unguarded behaviour instead of requiring a charger.
            if (tree != null && preset.id != "preset_battery_emergency") {
                assertEquals(
                    "Preset ${preset.id} must keep legacy conditions in sync with its tree",
                    tree.flattenConditions(),
                    template.conditions,
                )
            }
            assertTrue(
                "Preset ${preset.id} exceeds the multi-trigger limit",
                template.normalizedTriggers().size in 1..TriggerDefinitions.MAX_TRIGGERS,
            )
        }
    }

    @Test
    fun `multi trigger presets combine independent sources`() {
        val wakeUp = AutomationPresets.all.first { it.id == "preset_wake_up" }.template
        assertEquals(
            listOf(TriggerEvent.CHARGER_DISCONNECTED, TriggerEvent.SCREEN_ON),
            wakeUp.normalizedTriggers().map { it.event },
        )
        assertTrue(wakeUp.effectiveActions.contains(ActionType.DND_OFF))

        val nightQuiet = AutomationPresets.all.first { it.id == "preset_night_quiet" }.template
        assertEquals(
            listOf(TriggerEvent.TIME_SCHEDULE, TriggerEvent.SCREEN_OFF),
            nightQuiet.normalizedTriggers().map { it.event },
        )

        val commute = AutomationPresets.all.first { it.id == "preset_commute_mode" }.template
        assertEquals(
            listOf(TriggerEvent.WIFI_DISCONNECTED, TriggerEvent.CHARGER_DISCONNECTED),
            commute.normalizedTriggers().map { it.event },
        )
        assertEquals("Ev Wi-Fi", (commute.normalizedTriggers()[0] as TriggerDefinition.WifiDisconnected).wifiSsid)
    }

    @Test
    fun `battery emergency preset skips charging and focus preset negates home wifi`() {
        val emergency = AutomationPresets.all.first { it.id == "preset_battery_emergency" }.template
        assertTrue("Flat conditions must stay empty for older readers", emergency.conditions.isEmpty())
        val guard = emergency.conditionTree as ConditionNode.Group
        assertTrue(guard.negate)
        assertEquals(ConditionType.CHARGER_CONNECTED, (guard.children.single() as ConditionNode.Leaf).condition.type)

        val focus = AutomationPresets.all.first { it.id == "preset_focus_mode" }.template
        val root = focus.conditionTree as ConditionNode.Group
        assertEquals(ConditionCombinator.AND, root.combinator)
        assertFalse(root.negate)
        assertEquals(ConditionType.TIME_BETWEEN, (root.children[0] as ConditionNode.Leaf).condition.type)
        val notHome = root.children[1] as ConditionNode.Group
        assertTrue(notHome.negate)
        val homeWifi = (notHome.children.single() as ConditionNode.Leaf).condition
        assertEquals(ConditionType.WIFI_CONNECTED, homeWifi.type)
        assertEquals("Ev Wi-Fi", homeWifi.wifiSsid)
        assertTrue(focus.effectiveActions.contains(ActionType.DND_ON))
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
        assertTrue(shake.template.effectiveActions.contains(ActionType.TORCH_TOGGLE))
        assertTrue(shake.template.effectiveActions.contains(ActionType.VIBRATE))
    }
}
