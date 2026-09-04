package com.flowpilot.app

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flowpilot.app.data.backup.BackupManager
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.engine.AutomationService
import com.flowpilot.app.widget.FlowPilotWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator smoke checks. Requires API 26+ (module minSdk); no Robolectric assumptions.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeContractsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun manifest_deliversWidgetAndKeepsAutomationServicePrivate() {
        val service = packageManager.getServiceInfo(
            ComponentName(context, AutomationService::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse(service.exported)

        val receiver = packageManager.getReceiverInfo(
            ComponentName(context, FlowPilotWidgetProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        assertTrue(receiver.exported)
        assertEquals(
            R.xml.widget_flowpilot_control_info,
            receiver.metaData.getInt("android.appwidget.provider"),
        )
    }

    @Test
    fun import_disablesRules_andExportRedactsWebhookSecrets() {
        val rule = Automation(
            id = "instrumented-rule",
            name = "Webhook rule",
            action = ActionType.HTTP_WEBHOOK,
            webhookUrl = "https://user:password@example.test/hook",
            webhookHeaders = "Authorization: Bearer secret-token",
            webhookBody = "{\"secret\":\"payload\"}",
            createdAt = 1L,
        )

        val exported = BackupManager.exportToString(listOf(rule))
        assertFalse(exported.contains("password"))
        assertFalse(exported.contains("secret-token"))
        assertFalse(exported.contains("payload"))

        val imported = BackupManager.parseImport(exported).getOrThrow()
        assertEquals(1, imported.size)
        assertFalse(imported.single().enabled)
    }
}
