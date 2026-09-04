package com.flowpilot.app.engine

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ConditionType
import com.flowpilot.app.data.model.RuleCondition
import com.flowpilot.app.data.model.SmsMatchMode
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RuleEvaluatorSmsTest {

    private fun smsRule(
        id: String = "1",
        enabled: Boolean = true,
        senderFilter: String = "",
        matchMode: SmsMatchMode = SmsMatchMode.CONTAINS,
        keyword: String = "",
        cooldownMinutes: Int = 0,
        lastTriggeredAt: Long = 0L,
        conditions: List<RuleCondition> = emptyList(),
    ) = Automation(
        id = id,
        name = "SMS Rule",
        triggerEvent = TriggerEvent.SMS_RECEIVED,
        action = ActionType.SHOW_NOTIFICATION,
        enabled = enabled,
        smsSenderFilter = senderFilter,
        smsMatchMode = matchMode,
        smsKeyword = keyword,
        cooldownMinutes = cooldownMinutes,
        lastTriggeredAt = lastTriggeredAt,
        conditions = conditions,
        createdAt = 1000L,
    )

    @Test
    fun evaluateSms_matches_any_sender_when_filter_blank() {
        val rule = smsRule(senderFilter = "")
        val event = SmsReceivedEvent("+905551234567", "Hello FlowPilot")

        val result = RuleEvaluator.evaluateSms(listOf(rule), event)
        assertThat(result).containsExactly(rule)
    }

    @Test
    fun evaluateSms_matches_normalized_sender() {
        val rule = smsRule(senderFilter = "+90 555 123 45 67")
        val event = SmsReceivedEvent("+905551234567", "Hello FlowPilot")

        val result = RuleEvaluator.evaluateSms(listOf(rule), event)
        assertThat(result).containsExactly(rule)
    }

    @Test
    fun evaluateSms_filters_mismatched_sender() {
        val rule = smsRule(senderFilter = "+905559999999")
        val event = SmsReceivedEvent("+905551234567", "Hello FlowPilot")

        val result = RuleEvaluator.evaluateSms(listOf(rule), event)
        assertThat(result).isEmpty()
    }

    @Test
    fun evaluateSms_contains_mode_matches_substring_case_insensitive() {
        val rule = smsRule(matchMode = SmsMatchMode.CONTAINS, keyword = "onay kodu")
        val event = SmsReceivedEvent("+905551234567", "Bankanız: Onay Kodu 482912 olarak iletilmiştir.")

        val result = RuleEvaluator.evaluateSms(listOf(rule), event)
        assertThat(result).containsExactly(rule)
    }

    @Test
    fun evaluateSms_contains_mode_rejects_missing_keyword() {
        val rule = smsRule(matchMode = SmsMatchMode.CONTAINS, keyword = "ACIL")
        val event = SmsReceivedEvent("+905551234567", "Normal bilgilendirme mesajı")

        val result = RuleEvaluator.evaluateSms(listOf(rule), event)
        assertThat(result).isEmpty()
    }

    @Test
    fun evaluateSms_equals_mode_matches_exact_trimmed_text() {
        val rule = smsRule(matchMode = SmsMatchMode.EQUALS, keyword = "NEREDESIN")
        val eventMatch = SmsReceivedEvent("+905551234567", "  neredesin  ")
        val eventMismatch = SmsReceivedEvent("+905551234567", "neredesin acaba?")

        assertThat(RuleEvaluator.evaluateSms(listOf(rule), eventMatch)).containsExactly(rule)
        assertThat(RuleEvaluator.evaluateSms(listOf(rule), eventMismatch)).isEmpty()
    }

    @Test
    fun evaluateSms_starts_with_mode() {
        val rule = smsRule(matchMode = SmsMatchMode.STARTS_WITH, keyword = "KOD:")
        val eventMatch = SmsReceivedEvent("+905551234567", "kod: 99124")
        val eventMismatch = SmsReceivedEvent("+905551234567", "Giris icin kod: 99124")

        assertThat(RuleEvaluator.evaluateSms(listOf(rule), eventMatch)).containsExactly(rule)
        assertThat(RuleEvaluator.evaluateSms(listOf(rule), eventMismatch)).isEmpty()
    }

    @Test
    fun evaluateSms_regex_mode() {
        val rule = smsRule(matchMode = SmsMatchMode.REGEX, keyword = """\b\d{6}\b""")
        val eventMatch = SmsReceivedEvent("+905551234567", "Doğrulama kodunuz 654321 geçerlidir.")
        val eventMismatch = SmsReceivedEvent("+905551234567", "Telefon numaranız güncellendi.")

        assertThat(RuleEvaluator.evaluateSms(listOf(rule), eventMatch)).containsExactly(rule)
        assertThat(RuleEvaluator.evaluateSms(listOf(rule), eventMismatch)).isEmpty()
    }

    @Test
    fun evaluateSms_any_mode_matches_any_content() {
        val rule = smsRule(matchMode = SmsMatchMode.ANY)
        val event = SmsReceivedEvent("+905551234567", "Rastgele bir içerik 12345")

        assertThat(RuleEvaluator.evaluateSms(listOf(rule), event)).containsExactly(rule)
    }

    @Test
    fun evaluateSms_respects_cooldown_and_disabled() {
        val now = 100_000L
        val disabled = smsRule(enabled = false)
        val coolingDown = smsRule(
            cooldownMinutes = 5,
            lastTriggeredAt = now - 60_000L,
        )
        val event = SmsReceivedEvent("+905551234567", "Test")

        assertThat(RuleEvaluator.evaluateSms(listOf(disabled), event, nowMs = now)).isEmpty()
        assertThat(RuleEvaluator.evaluateSms(listOf(coolingDown), event, nowMs = now)).isEmpty()
    }

    @Test
    fun evaluateSms_respects_conditions() {
        val rule = smsRule(
            conditions = listOf(
                RuleCondition(type = ConditionType.BATTERY_BELOW, batteryLevel = 30)
            )
        )
        val event = SmsReceivedEvent("+905551234567", "Test")

        val stateHigh = LiveSystemState(batteryPercent = 80)
        val stateLow = LiveSystemState(batteryPercent = 20)

        assertThat(RuleEvaluator.evaluateSms(listOf(rule), event, liveState = stateHigh)).isEmpty()
        assertThat(RuleEvaluator.evaluateSms(listOf(rule), event, liveState = stateLow)).containsExactly(rule)
    }
}
