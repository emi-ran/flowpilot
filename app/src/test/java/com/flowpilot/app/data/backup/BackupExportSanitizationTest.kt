package com.flowpilot.app.data.backup

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerDefinition
import com.flowpilot.app.data.model.TriggerDefinitions
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupExportSanitizationTest {
    private val base = Automation(
        id = "pii",
        name = "PII rule",
        triggerEvent = TriggerEvent.SMS_RECEIVED,
        smsSenderFilter = "+15550109999",
        actions = listOf(ActionType.SEND_SMS, ActionType.CALL_NUMBER, ActionType.HTTP_WEBHOOK),
        action = ActionType.SEND_SMS,
        phoneNumber = "+15550101111",
        smsRecipient = "+15550102222",
        smsMessage = "hello",
        webhookUrl = "https://example.com/hook-secret",
        webhookHeaders = "Authorization: Bearer header-secret",
        webhookBody = "body-secret",
        jsonCommandSource = "json-secret",
        createdAt = 1L,
    )

    private fun exports(rule: Automation) = listOf(
        BackupManager.exportToString(listOf(rule)),
        BackupManager.exportSingleToString(rule),
    )

    @Test
    fun plainExportsContainNoPhoneNumbersOrWebhookSecrets() {
        for (json in exports(base)) {
            for (secret in listOf(
                "+15550101111", "+15550102222", "+15550109999",
                "hook-secret", "header-secret", "body-secret", "json-secret",
            )) {
                assertThat(json).doesNotContain(secret)
            }
        }
    }

    @Test
    fun multiTriggerSmsSenderFilterIsRedacted() {
        val rule = base.copy(
            triggerDefinitions = TriggerDefinitions(
                items = listOf(
                    TriggerDefinition.Sms(smsSenderFilter = "+15550108888", smsKeyword = "code"),
                    TriggerDefinition.WifiConnected("Home"),
                ),
            ),
        )
        for (json in exports(rule)) {
            assertThat(json).doesNotContain("+15550108888")
        }
        val sms = BackupManager.sanitizeForPlainExport(rule).triggerDefinitions!!.items
            .filterIsInstance<TriggerDefinition.Sms>().single()
        assertThat(sms.smsSenderFilter).isEqualTo(BackupManager.REDACTED_PLACEHOLDER)
        assertThat(sms.smsKeyword).isEqualTo("code")
    }

    @Test
    fun templateVariablesAndBlankValuesAreKept() {
        val safe = BackupManager.sanitizeForPlainExport(
            base.copy(smsRecipient = "\${sms.sender}", phoneNumber = "", smsSenderFilter = ""),
        )
        assertThat(safe.smsRecipient).isEqualTo("\${sms.sender}")
        assertThat(safe.phoneNumber).isEmpty()
        assertThat(safe.smsSenderFilter).isEmpty()
    }

    @Test
    fun sanitizedExportStillImportsAndSourceIsNotMutated() {
        for (json in exports(base)) {
            val imported = BackupManager.parseImport(json).getOrThrow().single()
            assertThat(imported.phoneNumber).isEqualTo(BackupManager.REDACTED_PLACEHOLDER)
            assertThat(imported.smsRecipient).isEqualTo(BackupManager.REDACTED_PLACEHOLDER)
            assertThat(imported.webhookUrl).isEmpty()
        }
        assertThat(base.phoneNumber).isEqualTo("+15550101111")
    }

    @Test
    fun encryptedExportKeepsFullValues() {
        val json = BackupManager.exportEncryptedToString(listOf(base), "password-123")
        assertThat(json).doesNotContain("+15550101111")
        val restored = BackupManager.parseEncryptedImport(json, "password-123").getOrThrow().single()
        assertThat(restored.phoneNumber).isEqualTo("+15550101111")
        assertThat(restored.smsRecipient).isEqualTo("+15550102222")
        assertThat(restored.webhookUrl).isEqualTo("https://example.com/hook-secret")
    }
}
