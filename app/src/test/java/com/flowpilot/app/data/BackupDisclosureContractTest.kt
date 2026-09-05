package com.flowpilot.app.data

import com.flowpilot.app.data.backup.BackupManager
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupDisclosureContractTest {
    @Test
    fun bothExportsOmitWebhookFieldsButRetainDisclosedPersonalDataWithoutMutatingSource() {
        val rule = Automation(
            id = "disclosure-test",
            name = "Personal rule",
            enabled = true,
            triggerEvent = TriggerEvent.BATTERY_BELOW,
            webhookUrl = "https://example.com/private-token",
            webhookHeaders = "Authorization: Bearer private-secret",
            webhookBody = "private-payload",
            phoneNumber = "+15550102030",
            smsRecipient = "+15550104050",
            smsMessage = "Personal message",
            wifiSsid = "Home network",
            bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF",
            nfcTagId = "01234567",
            url = "https://example.com/personal-link",
        )
        for (json in listOf(BackupManager.exportToString(listOf(rule)), BackupManager.exportSingleToString(rule))) {
            val imported = BackupManager.parseImport(json).getOrThrow().single()
            assertThat(imported).isEqualTo(rule.copy(
                enabled = false, webhookUrl = "", webhookHeaders = "", webhookBody = "",
            ))
            assertThat(json).doesNotContain("private-token")
            assertThat(json).doesNotContain("private-secret")
            assertThat(json).doesNotContain("private-payload")
        }
        assertThat(rule.enabled).isTrue()
        assertThat(rule.webhookUrl).isEqualTo("https://example.com/private-token")
        assertThat(rule.webhookHeaders).isEqualTo("Authorization: Bearer private-secret")
        assertThat(rule.webhookBody).isEqualTo("private-payload")
    }
}
