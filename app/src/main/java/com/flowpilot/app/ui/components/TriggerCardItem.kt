@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.flowpilot.app.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.data.model.TriggerEvent

fun triggerIcon(event: TriggerEvent): ImageVector = when (event) {
    TriggerEvent.APP_OPENED -> Icons.Default.Apps
    TriggerEvent.APP_CLOSED -> Icons.Default.Close
    TriggerEvent.CHARGER_CONNECTED, TriggerEvent.CHARGER_DISCONNECTED -> Icons.Default.Power
    TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> Icons.Default.BatteryAlert
    TriggerEvent.SCREEN_ON, TriggerEvent.SCREEN_OFF -> Icons.Default.StayCurrentPortrait
    TriggerEvent.TIME_SCHEDULE -> Icons.Default.Schedule
    TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED -> Icons.Default.Wifi
    TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED -> Icons.Default.Bluetooth
    TriggerEvent.NFC_TAG_SCANNED -> Icons.Default.Nfc
    TriggerEvent.NOTIFICATION_RECEIVED -> Icons.Default.NotificationsActive
    TriggerEvent.CALL_RINGING, TriggerEvent.CALL_ANSWERED, TriggerEvent.CALL_OUTGOING, TriggerEvent.CALL_ENDED -> Icons.Default.Phone
    TriggerEvent.DEVICE_FLIPPED_DOWN, TriggerEvent.DEVICE_FLIPPED_UP -> Icons.Default.ScreenRotation
    TriggerEvent.DEVICE_SHAKE -> Icons.Default.Vibration
    TriggerEvent.DEVICE_UNLOCKED -> Icons.Default.LockOpen
    TriggerEvent.LIGHT_BELOW, TriggerEvent.LIGHT_ABOVE -> Icons.Default.LightMode
}

@Composable
fun TriggerCardItem(
    event: TriggerEvent,
    onChangeTrigger: () -> Unit,
    // App
    pkg: String = "",
    appName: String = "",
    onOpenAppPicker: () -> Unit = {},
    // Schedule
    scheduledMinute: Int = 0,
    scheduledDays: Set<Int> = emptySet(),
    onOpenTimePicker: () -> Unit = {},
    onDaysChange: (Set<Int>) -> Unit = {},
    // Battery
    batteryLevel: Int = 50,
    onBatteryLevelChange: (Int) -> Unit = {},
    // Wi-Fi
    wifiSsid: String = "",
    onWifiSsidChange: (String) -> Unit = {},
    // Bluetooth
    bluetoothAddress: String = "",
    bluetoothName: String = "",
    onBluetoothChange: (String, String) -> Unit = { _, _ -> },
    // NFC
    nfcTagId: String = "",
    onNfcTagChange: (String) -> Unit = {},
    onScanNfcTag: () -> Unit = {},
    // Notification
    notificationAppPackage: String = "",
    notificationAppName: String = "",
    notificationKeyword: String = "",
    onOpenNotificationAppPicker: () -> Unit = {},
    onNotificationKeywordChange: (String) -> Unit = {},
    // Flip
    flipScreenOffDetection: Boolean = false,
    onFlipScreenOffChange: (Boolean) -> Unit = {},
    // Light
    lightLux: Int = 10,
    onLightLuxChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    var liveAmbientLux by remember { mutableStateOf<Float?>(null) }

    val isLightTrigger = event == TriggerEvent.LIGHT_BELOW || event == TriggerEvent.LIGHT_ABOVE
    DisposableEffect(isLightTrigger) {
        if (!isLightTrigger) return@DisposableEffect onDispose {}
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
            liveAmbientLux = null
            return@DisposableEffect onDispose {}
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                if (ev.sensor.type == Sensor.TYPE_LIGHT) {
                    liveAmbientLux = ev.values.getOrNull(0)
                }
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sm.unregisterListener(listener)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Main Trigger Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChangeTrigger),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if ((event == TriggerEvent.APP_OPENED || event == TriggerEvent.APP_CLOSED) && pkg.isNotEmpty()) {
                        AppIconImage(
                            packageName = pkg,
                            modifier = Modifier.size(28.dp),
                            fallbackIcon = triggerIcon(event),
                        )
                    } else if (event == TriggerEvent.NOTIFICATION_RECEIVED && notificationAppPackage.isNotEmpty()) {
                        AppIconImage(
                            packageName = notificationAppPackage,
                            modifier = Modifier.size(28.dp),
                            fallbackIcon = triggerIcon(event),
                        )
                    } else {
                        Icon(
                            triggerIcon(event),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        event.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitle = when (event) {
                        TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> if (pkg.isEmpty()) "Tap to choose app" else appName.ifBlank { pkg }
                        TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED -> if (wifiSsid.isBlank()) "Any Wi-Fi network" else wifiSsid
                        TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED -> if (bluetoothAddress.isBlank()) "Choose device" else bluetoothName.ifBlank { bluetoothAddress }
                        TriggerEvent.TIME_SCHEDULE -> "%02d:%02d".format(scheduledMinute / 60, scheduledMinute % 60)
                        TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> "Threshold: $batteryLevel%"
                        TriggerEvent.CHARGER_CONNECTED -> "When plugged into power"
                        TriggerEvent.CHARGER_DISCONNECTED -> "When unplugged from power"
                        TriggerEvent.SCREEN_ON -> "When screen turns on"
                        TriggerEvent.SCREEN_OFF -> "When screen turns off"
                        TriggerEvent.DEVICE_FLIPPED_DOWN -> "Placed face down on surface"
                        TriggerEvent.DEVICE_FLIPPED_UP -> "Turned face up to normal"
                        TriggerEvent.NFC_TAG_SCANNED -> if (nfcTagId.isBlank()) "Tap to scan tag" else nfcTagId
                        TriggerEvent.NOTIFICATION_RECEIVED -> if (notificationAppPackage.isBlank()) "Choose app" else notificationAppName.ifBlank { notificationAppPackage }
                        TriggerEvent.CALL_RINGING -> "Incoming call starts ringing"
                        TriggerEvent.CALL_ANSWERED -> "Call is answered"
                        TriggerEvent.CALL_OUTGOING -> "Outgoing call placed"
                        TriggerEvent.CALL_ENDED -> "Call ends"
                        TriggerEvent.DEVICE_SHAKE -> "When device is shaken"
                        TriggerEvent.DEVICE_UNLOCKED -> "When device is unlocked"
                        TriggerEvent.LIGHT_BELOW -> "Ambient light drops below $lightLux lx"
                        TriggerEvent.LIGHT_ABOVE -> "Ambient light rises above $lightLux lx"
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Change trigger",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Embedded specific controls if needed
            when (event) {
                TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> {
                    Spacer(Modifier.height(10.dp))
                    OutlinedCard(
                        onClick = onOpenAppPicker,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(
                                packageName = pkg,
                                modifier = Modifier.size(28.dp),
                                fallbackIcon = Icons.Default.Apps,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (pkg.isEmpty()) "Tap to choose target app" else "$appName ($pkg)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TriggerEvent.TIME_SCHEDULE -> {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedCard(
                            onClick = onOpenTimePicker,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("%02d:%02d".format(scheduledMinute / 60, scheduledMinute % 60), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = scheduledDays.isEmpty(),
                                onClick = { onDaysChange(emptySet()) },
                                label = { Text("Daily") },
                            )
                            FilterChip(
                                selected = scheduledDays == setOf(1, 2, 3, 4, 5),
                                onClick = { onDaysChange(setOf(1, 2, 3, 4, 5)) },
                                label = { Text("Weekdays") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                            val day = index + 1
                            FilterChip(
                                selected = scheduledDays.isNotEmpty() && day in scheduledDays,
                                onClick = {
                                    val next = if (day in scheduledDays) scheduledDays - day else scheduledDays + day
                                    onDaysChange(next)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("$batteryLevel%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp))
                        Slider(
                            value = batteryLevel.toFloat(),
                            onValueChange = { onBatteryLevelChange(it.toInt()) },
                            valueRange = 1f..100f,
                            steps = 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED -> {
                    Spacer(Modifier.height(10.dp))
                    WifiSsidPickerField(
                        ssid = wifiSsid,
                        onSsidChange = onWifiSsidChange,
                        label = "Wi-Fi SSID (empty for any)",
                    )
                }
                TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED -> {
                    Spacer(Modifier.height(10.dp))
                    BluetoothDevicePickerField(
                        address = bluetoothAddress,
                        name = bluetoothName,
                        onDeviceSelected = onBluetoothChange,
                    )
                }
                TriggerEvent.NFC_TAG_SCANNED -> {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = nfcTagId,
                        onValueChange = { onNfcTagChange(it.uppercase()) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("NFC Tag UID (hex)") },
                        placeholder = { Text("e.g. 04A1B2C3D4E5F6") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        supportingText = {
                            Text("Tap a tag to the phone while app is open to auto-capture UID.")
                        },
                    )
                }
                TriggerEvent.NOTIFICATION_RECEIVED -> {
                    Spacer(Modifier.height(10.dp))
                    OutlinedCard(
                        onClick = onOpenNotificationAppPicker,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(
                                packageName = notificationAppPackage,
                                modifier = Modifier.size(28.dp),
                                fallbackIcon = Icons.Default.NotificationsActive,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (notificationAppPackage.isEmpty()) "Tap to select app" else "$notificationAppName ($notificationAppPackage)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = notificationKeyword,
                        onValueChange = onNotificationKeywordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Keyword filter (optional)") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                }
                TriggerEvent.DEVICE_FLIPPED_DOWN, TriggerEvent.DEVICE_FLIPPED_UP -> {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = flipScreenOffDetection,
                            onCheckedChange = onFlipScreenOffChange,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Detect while screen is off (uses slightly more battery)", style = MaterialTheme.typography.bodySmall)
                    }
                }
                TriggerEvent.LIGHT_BELOW, TriggerEvent.LIGHT_ABOVE -> {
                    Spacer(Modifier.height(12.dp))

                    // Live Lux Status Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Current room light",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (liveAmbientLux != null) "%.0f lx".format(liveAmbientLux) else "Reading sensor...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (liveAmbientLux != null) {
                                FilledTonalButton(
                                    onClick = { onLightLuxChange(liveAmbientLux!!.toInt().coerceIn(1, 1000)) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Set as target", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Target Threshold Slider & Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (event == TriggerEvent.LIGHT_BELOW) "Trigger when darker than" else "Trigger when brighter than",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                "$lightLux lx",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Slider(
                        value = lightLux.toFloat().coerceIn(1f, 1000f),
                        onValueChange = { onLightLuxChange(it.toInt()) },
                        valueRange = 1f..1000f,
                        steps = 0,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Presets in a responsive FlowRow
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        listOf(5 to "Dark room (5 lx)", 20 to "Dim / Night (20 lx)", 100 to "Indoor (100 lx)", 300 to "Office (300 lx)", 600 to "Daylight (600 lx)").forEach { (presetLux, label) ->
                            FilterChip(
                                selected = lightLux == presetLux,
                                onClick = { onLightLuxChange(presetLux) },
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
                TriggerEvent.DEVICE_SHAKE -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Shake phone firmly to trigger. Active while screen is on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TriggerEvent.DEVICE_UNLOCKED -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Triggers each time the phone is unlocked with fingerprint, face, or PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    // Triggers with no extra configuration
                }
            }
        }
    }
}
