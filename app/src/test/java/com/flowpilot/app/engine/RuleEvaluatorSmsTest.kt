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
    fun evaluateSms_blank_keyword_boundaries_differ_by_match_mode() {
        val contains = smsRule(id = "contains", matchMode = SmsMatchMode.CONTAINS, keyword = "  ")
        val startsWith = smsRule(id = "starts", matchMode = SmsMatchMode.STARTS_WITH, keyword = "")
        val equals = smsRule(id = "equals", matchMode = SmsMatchMode.EQUALS, keyword = "")
        val rules = listOf(contains, startsWith, equals)

        val nonBlank = SmsReceivedEvent("+905551234567", "payload")
        val blank = SmsReceivedEvent("+905551234567", "   ")

        // Blank CONTAINS / STARTS_WITH act as "any body"; blank EQUALS only accepts a blank body.
        assertThat(RuleEvaluator.evaluateSms(rules, nonBlank)).containsExactly(contains, startsWith)
        assertThat(RuleEvaluator.evaluateSms(rules, blank)).containsExactly(contains, startsWith, equals)
    }

    @Test
    fun evaluateSms_invalid_regex_pattern_never_matches() {
        val rule = smsRule(matchMode = SmsMatchMode.REGEX, keyword = "[")
        val event = SmsReceivedEvent("+905551234567", "Anything [")

        assertThat(RuleEvaluator.evaluateSms(listOf(rule), event)).isEmpty()
    }

    @Test
    fun evaluateSms_national_sender_filter_matches_international_event_number() {
        val rule = smsRule(senderFilter = "555 123 45 67")
        val matching = SmsReceivedEvent("+90 555 123 45 67", "Hello")
        val mismatching = SmsReceivedEvent("+90 555 123 45 68", "Hello")

        assertThat(RuleEvaluator.evaluateSms(listOf(rule), matching)).containsExactly(rule)
        assertThat(RuleEvaluator.evaluateSms(listOf(rule), mismatching)).isEmpty()
    }

    @Test
    fun evaluateSms_cooldown_expiry_boundary_allows_execution() {
        val now = 1_000_000L
        val fiveMinutes = 5 * 60_000L
        val justBeforeExpiry = smsRule(
            id = "before",
            cooldownMinutes = 5,
            lastTriggeredAt = now - fiveMinutes + 1,
        )
        val exactlyExpired = smsRule(
            id = "expired",
            cooldownMinutes = 5,
            lastTriggeredAt = now - fiveMinutes,
        )
        val event = SmsReceivedEvent("+905551234567", "Test")

        assertThat(RuleEvaluator.evaluateSms(listOf(justBeforeExpiry), event, nowMs = now)).isEmpty()
        assertThat(RuleEvaluator.evaluateSms(listOf(exactlyExpired), event, nowMs = now))
            .containsExactly(exactlyExpired)
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
