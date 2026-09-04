package com.flowpilot.app.ui.util

import com.flowpilot.app.data.model.*
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationTest {

    @Test
    fun `all trigger events have valid label resources`() {
        for (event in TriggerEvent.entries) {
            assertTrue("TriggerEvent ${event.name} should have non-zero labelRes", event.labelRes != 0)
        }
    }

    @Test
    fun `all action types have valid label resources`() {
        for (action in ActionType.entries) {
            assertTrue("ActionType ${action.name} should have non-zero labelRes", action.labelRes != 0)
        }
    }

    @Test
    fun `all condition types have valid label resources`() {
        for (cond in ConditionType.entries) {
            assertTrue("ConditionType ${cond.name} should have non-zero labelRes", cond.labelRes != 0)
        }
    }

    @Test
    fun `all trigger categories have valid label resources`() {
        for (cat in TriggerCategory.entries) {
            assertTrue("TriggerCategory ${cat.name} should have non-zero labelRes", cat.labelRes != 0)
        }
    }

    @Test
    fun `all action categories have valid label resources`() {
        for (cat in ActionCategory.entries) {
            assertTrue("ActionCategory ${cat.name} should have non-zero labelRes", cat.labelRes != 0)
        }
    }

    @Test
    fun `all preset categories have valid label resources`() {
        for (cat in PresetCategory.entries) {
            assertTrue("PresetCategory ${cat.name} should have non-zero labelRes", cat.labelRes != 0)
        }
    }

    @Test
    fun `all sms match modes have valid label resources`() {
        for (mode in SmsMatchMode.entries) {
            assertTrue("SmsMatchMode ${mode.name} should have non-zero labelRes", mode.labelRes != 0)
        }
    }

    @Test
    fun `all vibration patterns have valid label resources`() {
        for (pattern in VibrationPattern.entries) {
            assertTrue("VibrationPattern ${pattern.name} should have non-zero labelRes", pattern.labelRes != 0)
        }
    }

    @Test
    fun `all sound presets have valid label resources`() {
        for (preset in SoundPreset.entries) {
            assertTrue("SoundPreset ${preset.name} should have non-zero labelRes", preset.labelRes != 0)
        }
    }
}