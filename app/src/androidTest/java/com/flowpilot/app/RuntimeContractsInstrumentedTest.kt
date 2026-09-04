package com.flowpilot.app

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowpilot.app.engine.BootReceiver
import com.flowpilot.app.engine.SmsReceiver
import com.flowpilot.app.widget.FlowPilotWidgetToggleReceiver
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
    fun manifest_keepsWidgetToggleReceiverPrivate() {
        val receiver = packageManager.getReceiverInfo(
            ComponentName(context, FlowPilotWidgetToggleReceiver::class.java),
            0,
        )
        assertFalse(receiver.exported)
    }

    @Test
    fun manifest_protectsBootAndSmsReceivers() {
        assertEquals(
            "android.permission.RECEIVE_BOOT_COMPLETED",
            packageManager.getReceiverInfo(
                ComponentName(context, BootReceiver::class.java),
                0,
            ).permission,
        )
        assertEquals(
            "android.permission.BROADCAST_SMS",
            packageManager.getReceiverInfo(
                ComponentName(context, SmsReceiver::class.java),
                0,
            ).permission,
        )
    }

    @Test
    fun manifest_declaresAutomationServiceForegroundTypesAndSubtype() {
        val service = packageManager.getServiceInfo(
            ComponentName(context, AutomationService::class.java),
            PackageManager.GET_META_DATA,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue(service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            assertTrue(service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0)
        }
        if (Build.VERSION.SDK_INT >= 35) {
            assertEquals(
                "Runs user-started automation monitoring and scheduled rules, including foreground-app transitions, broadcasts, sensors, NFC, calls, SMS, and optional background GPS acquisition.",
                packageManager.getProperty(
                    "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
                    ComponentName(context, AutomationService::class.java),
                ).getString(),
            )
        }
    }

    @Test
    fun manifest_usesSafeApplicationAndProviderContracts() {
        val application = packageManager.getApplicationInfo(context.packageName, 0)
        assertTrue(application.flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0)

        val fileProvider = packageManager.resolveContentProvider(
            "${context.packageName}.fileprovider",
            PackageManager.GET_META_DATA,
        )
        assertTrue(fileProvider != null)
        assertFalse(fileProvider!!.exported)
        assertTrue(fileProvider.grantUriPermissions)
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
