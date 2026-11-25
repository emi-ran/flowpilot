package com.flowpilot.app.engine

import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class SoundStateTransitionsTest {
    @After fun cleanup() = OwnSoundChangeGuard.reset()

    private fun rule(event: TriggerEvent, enabled: Boolean = true, cooldownMinutes: Int = 0, lastTriggeredAt: Long = 0L) =
        Automation(id = event.name, name = event.name, createdAt = 1L, triggerEvent = event,
            enabled = enabled, cooldownMinutes = cooldownMinutes, lastTriggeredAt = lastTriggeredAt)

    // Mappings

    @Test fun ringerMapping() {
        assertThat(RingerState.fromAudioMode(0)).isEqualTo(RingerState.SILENT)
        assertThat(RingerState.fromAudioMode(1)).isEqualTo(RingerState.VIBRATE)
        assertThat(RingerState.fromAudioMode(2)).isEqualTo(RingerState.NORMAL)
        assertThat(RingerState.fromAudioMode(-1)).isNull()
        assertThat(RingerState.fromAudioMode(3)).isNull()
        assertThat(RingerState.fromAudioMode(Int.MAX_VALUE)).isNull()
    }

    @Test fun ringerTriggerEvents() {
        assertThat(RingerState.NORMAL.triggerEvent).isEqualTo(TriggerEvent.RINGER_NORMAL)
        assertThat(RingerState.VIBRATE.triggerEvent).isEqualTo(TriggerEvent.RINGER_VIBRATE)
        assertThat(RingerState.SILENT.triggerEvent).isEqualTo(TriggerEvent.RINGER_SILENT)
    }

    @Test fun dndMapping() {
        assertThat(DndState.fromInterruptionFilter(1)).isEqualTo(DndState.OFF)
        for (f in listOf(2, 3, 4)) assertThat(DndState.fromInterruptionFilter(f)).isEqualTo(DndState.ON)
        assertThat(DndState.fromInterruptionFilter(0)).isNull()
        assertThat(DndState.fromInterruptionFilter(-5)).isNull()
        assertThat(DndState.fromInterruptionFilter(99)).isNull()
        assertThat(DndState.ON.triggerEvent).isEqualTo(TriggerEvent.DND_ENABLED)
        assertThat(DndState.OFF.triggerEvent).isEqualTo(TriggerEvent.DND_DISABLED)
    }

    // Guard

    @Test fun guardWindow() {
        assertThat(OwnSoundChangeGuard.isSuppressed(1000, null)).isFalse()
        assertThat(OwnSoundChangeGuard.isSuppressed(1000, 1000)).isTrue()
        assertThat(OwnSoundChangeGuard.isSuppressed(2999, 1000)).isTrue()
        assertThat(OwnSoundChangeGuard.isSuppressed(3000, 1000)).isFalse()
        assertThat(OwnSoundChangeGuard.isSuppressed(5000, 1000)).isFalse()
    }

    @Test fun guardMarkAndReset() {
        assertThat(OwnSoundChangeGuard.lastMark()).isNull()
        OwnSoundChangeGuard.markOwnChange(42L)
        assertThat(OwnSoundChangeGuard.lastMark()).isEqualTo(42L)
        OwnSoundChangeGuard.reset()
        assertThat(OwnSoundChangeGuard.lastMark()).isNull()
    }

    // Reducer

    @Test fun firstValueIsBaselineOnly() {
        val r = StateTransitionReducer<RingerState>()
        assertThat(r.observe(RingerState.NORMAL, 0, null)).isNull()
    }

    @Test fun seededBaselineSuppressesStickyRepeat() {
        val r = StateTransitionReducer<RingerState>()
        r.seed(RingerState.SILENT)
        assertThat(r.observe(RingerState.SILENT, 0, null)).isNull()
        assertThat(r.observe(RingerState.NORMAL, 10, null)).isEqualTo(RingerState.NORMAL)
    }

    @Test fun repeatedValueDoesNotFire() {
        val r = StateTransitionReducer<DndState>()
        r.seed(DndState.OFF)
        assertThat(r.observe(DndState.ON, 0, null)).isEqualTo(DndState.ON)
        assertThat(r.observe(DndState.ON, 1, null)).isNull()
        assertThat(r.observe(DndState.OFF, 2, null)).isEqualTo(DndState.OFF)
    }

    @Test fun nullIsIgnoredAndKeepsBaseline() {
        val r = StateTransitionReducer<DndState>()
        r.seed(DndState.OFF)
        assertThat(r.observe(null, 0, null)).isNull()
        assertThat(r.observe(DndState.OFF, 1, null)).isNull()
        assertThat(r.observe(DndState.ON, 2, null)).isEqualTo(DndState.ON)
    }

    @Test fun seedNullDoesNotSetBaseline() {
        val r = StateTransitionReducer<DndState>()
        r.seed(null)
        assertThat(r.observe(DndState.ON, 0, null)).isNull()
    }

    @Test fun ownChangeInsideWindowIsSuppressed() {
        val r = StateTransitionReducer<RingerState>()
        r.seed(RingerState.NORMAL)
        assertThat(r.observe(RingerState.SILENT, 1500, 1000)).isNull()
    }

    @Test fun changeAtWindowBoundaryFires() {
        val r = StateTransitionReducer<RingerState>()
        r.seed(RingerState.NORMAL)
        assertThat(r.observe(RingerState.SILENT, 3000, 1000)).isEqualTo(RingerState.SILENT)
    }

    @Test fun suppressedChangeStillUpdatesBaseline() {
        val r = StateTransitionReducer<RingerState>()
        r.seed(RingerState.NORMAL)
        assertThat(r.observe(RingerState.SILENT, 1100, 1000)).isNull()
        // Same value later is a repeat, not a transition.
        assertThat(r.observe(RingerState.SILENT, 9000, 1000)).isNull()
        assertThat(r.observe(RingerState.NORMAL, 9100, 1000)).isEqualTo(RingerState.NORMAL)
    }

    @Test fun resetClearsBaseline() {
        val r = StateTransitionReducer<RingerState>()
        r.seed(RingerState.NORMAL)
        r.reset()
        assertThat(r.observe(RingerState.SILENT, 0, null)).isNull()
    }

    @Test fun reducersAreIndependent() {
        val a = StateTransitionReducer<RingerState>()
        val b = StateTransitionReducer<RingerState>()
        a.seed(RingerState.NORMAL)
        b.seed(RingerState.SILENT)
        assertThat(a.observe(RingerState.SILENT, 0, null)).isEqualTo(RingerState.SILENT)
        assertThat(b.observe(RingerState.SILENT, 0, null)).isNull()
    }

    // Demand

    @Test fun demandPerEvent() {
        val ringer = listOf(TriggerEvent.RINGER_NORMAL, TriggerEvent.RINGER_VIBRATE, TriggerEvent.RINGER_SILENT)
        val dnd = listOf(TriggerEvent.DND_ENABLED, TriggerEvent.DND_DISABLED)
        assertThat(TriggerListenerDemand.from(emptyList()).needsRinger).isFalse()
        assertThat(TriggerListenerDemand.from(emptyList()).needsDnd).isFalse()
        for (e in ringer) {
            val d = TriggerListenerDemand.from(listOf(rule(e)))
            assertThat(d.needsRinger).isTrue()
            assertThat(d.needsDnd).isFalse()
            assertThat(TriggerListenerDemand.from(listOf(rule(e, enabled = false))).needsRinger).isFalse()
        }
        for (e in dnd) {
            val d = TriggerListenerDemand.from(listOf(rule(e)))
            assertThat(d.needsDnd).isTrue()
            assertThat(d.needsRinger).isFalse()
            assertThat(TriggerListenerDemand.from(listOf(rule(e, enabled = false))).needsDnd).isFalse()
        }
    }

    @Test fun unrelatedTriggersDoNotNeedSoundReceivers() {
        val d = TriggerListenerDemand.from(listOf(rule(TriggerEvent.SCREEN_ON), rule(TriggerEvent.CHARGER_CONNECTED)))
        assertThat(d.needsRinger).isFalse()
        assertThat(d.needsDnd).isFalse()
    }

    // Evaluator

    @Test fun evaluateRingerMatchesOnlyTargetState() {
        val silent = rule(TriggerEvent.RINGER_SILENT)
        val normal = rule(TriggerEvent.RINGER_NORMAL)
        val rules = listOf(silent, normal)
        assertThat(RuleEvaluator.evaluateRinger(rules, RingerState.SILENT)).containsExactly(silent)
        assertThat(RuleEvaluator.evaluateRinger(rules, RingerState.NORMAL)).containsExactly(normal)
        assertThat(RuleEvaluator.evaluateRinger(rules, RingerState.VIBRATE)).isEmpty()
    }

    @Test fun evaluateRingerSkipsDisabledAndCoolingDown() {
        val now = 10_000_000L
        val disabled = rule(TriggerEvent.RINGER_SILENT, enabled = false)
        val cooling = rule(TriggerEvent.RINGER_SILENT, cooldownMinutes = 5, lastTriggeredAt = now - 1000)
        assertThat(RuleEvaluator.evaluateRinger(listOf(disabled, cooling), RingerState.SILENT, nowMs = now)).isEmpty()
    }

    @Test fun evaluateDndMatchesOnlyTargetState() {
        val on = rule(TriggerEvent.DND_ENABLED)
        val off = rule(TriggerEvent.DND_DISABLED)
        assertThat(RuleEvaluator.evaluateDnd(listOf(on, off), DndState.ON)).containsExactly(on)
        assertThat(RuleEvaluator.evaluateDnd(listOf(on, off), DndState.OFF)).containsExactly(off)
        assertThat(RuleEvaluator.evaluateDnd(listOf(rule(TriggerEvent.SCREEN_ON)), DndState.ON)).isEmpty()
    }

    @Test fun evaluateDndSkipsDisabled() {
        assertThat(RuleEvaluator.evaluateDnd(listOf(rule(TriggerEvent.DND_ENABLED, enabled = false)), DndState.ON)).isEmpty()
    }
}
