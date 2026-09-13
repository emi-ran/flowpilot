package com.flowpilot.app.ui.util

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationNameGeneratorTest {

    @Test
    fun automaticName_usesSelectedAppLanguageLabels() {
        val context: android.content.Context = RuntimeEnvironment.getApplication()
            .localizedForAppLanguage("tr")

        val name = automaticAutomationName(
            context = context,
            trigger = TriggerEvent.APP_OPENED,
            actions = listOf(ActionType.SHOW_NOTIFICATION),
            appName = "Akbank",
            appPackage = "com.akbank",
            scheduledMinute = 0,
            batteryLevel = 50,
            wifiSsid = "",
            bluetoothDeviceName = "",
            bluetoothDeviceAddress = "",
            nfcTagId = "",
            notificationAppName = "",
            notificationAppPackage = "",
            lightLux = 10,
            geofenceName = "",
            geofenceRadiusMeters = 150,
        )

        assertThat(name).isEqualTo("Akbank · Bildirim göster")
    }
}
