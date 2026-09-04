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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.data.model.SmsMatchMode
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.ui.util.localizedLabel

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
    TriggerEvent.SMS_RECEIVED -> Icons.Default.Sms
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
    // SMS
    smsSenderFilter: String = "",
    onSmsSenderFilterChange: (String) -> Unit = {},
    smsMatchMode: com.flowpilot.app.data.model.SmsMatchMode = com.flowpilot.app.data.model.SmsMatchMode.CONTAINS,
    onSmsMatchModeChange: (com.flowpilot.app.data.model.SmsMatchMode) -> Unit = {},
    smsKeyword: String = "",
    onSmsKeywordChange: (String) -> Unit = {},
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
                        event.localizedLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val subtitle = when (event) {
                        TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED -> if (pkg.isEmpty()) stringResource(R.string.tap_to_choose_app) else appName.ifBlank { pkg }
                        TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED -> if (wifiSsid.isBlank()) stringResource(R.string.trigger_wifi_any) else wifiSsid
                        TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED -> if (bluetoothAddress.isBlank()) stringResource(R.string.bluetooth_device_choose) else bluetoothName.ifBlank { bluetoothAddress }
                        TriggerEvent.TIME_SCHEDULE -> "%02d:%02d".format(scheduledMinute / 60, scheduledMinute % 60)
                        TriggerEvent.BATTERY_BELOW, TriggerEvent.BATTERY_ABOVE -> stringResource(R.string.detail_threshold_battery, batteryLevel)
                        TriggerEvent.CHARGER_CONNECTED -> stringResource(R.string.trigger_charger_connected_desc)
                        TriggerEvent.CHARGER_DISCONNECTED -> stringResource(R.string.trigger_charger_disconnected_desc)
                        TriggerEvent.SCREEN_ON -> stringResource(R.string.trigger_screen_on_desc)
                        TriggerEvent.SCREEN_OFF -> stringResource(R.string.trigger_screen_off_desc)
                        TriggerEvent.DEVICE_FLIPPED_DOWN -> stringResource(R.string.trigger_device_flipped_down_desc)
                        TriggerEvent.DEVICE_FLIPPED_UP -> stringResource(R.string.trigger_device_flipped_up_desc)
                        TriggerEvent.NFC_TAG_SCANNED -> if (nfcTagId.isBlank()) stringResource(R.string.detail_tag_id, "—") else nfcTagId
                        TriggerEvent.NOTIFICATION_RECEIVED -> if (notificationAppPackage.isBlank()) stringResource(R.string.notification_choose_app) else notificationAppName.ifBlank { notificationAppPackage }
                        TriggerEvent.CALL_RINGING -> stringResource(R.string.trigger_call_ringing_desc)
                        TriggerEvent.CALL_ANSWERED -> stringResource(R.string.trigger_call_answered_desc)
                        TriggerEvent.CALL_OUTGOING -> stringResource(R.string.trigger_call_outgoing_desc)
                        TriggerEvent.CALL_ENDED -> stringResource(R.string.trigger_call_ended_desc)
                        TriggerEvent.DEVICE_SHAKE -> stringResource(R.string.trigger_device_shake_desc)
                        TriggerEvent.DEVICE_UNLOCKED -> stringResource(R.string.trigger_device_unlocked_desc)
                        TriggerEvent.LIGHT_BELOW, TriggerEvent.LIGHT_ABOVE -> stringResource(R.string.detail_threshold_light, lightLux)
                        TriggerEvent.SMS_RECEIVED -> {
                            val sender = if (smsSenderFilter.isBlank()) stringResource(R.string.detail_any_sender) else smsSenderFilter
                            val filterDesc = when (smsMatchMode) {
                                SmsMatchMode.ANY -> stringResource(R.string.sms_any_sms)
                                SmsMatchMode.CONTAINS -> stringResource(R.string.sms_contains_quoted, smsKeyword)
                                SmsMatchMode.EQUALS -> stringResource(R.string.sms_equals_quoted, smsKeyword)
                                SmsMatchMode.STARTS_WITH -> stringResource(R.string.sms_starts_with_quoted, smsKeyword)
                                SmsMatchMode.REGEX -> stringResource(R.string.sms_regex_quoted, smsKeyword)
                            }
                            "$sender • $filterDesc"
                        }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.choose_trigger),
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
                                if (pkg.isEmpty()) stringResource(R.string.tap_to_choose_target_app) else "$appName ($pkg)",
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
                                label = { Text(stringResource(R.string.schedule_daily)) },
                            )
                            FilterChip(
                                selected = scheduledDays == setOf(1, 2, 3, 4, 5),
                                onClick = { onDaysChange(setOf(1, 2, 3, 4, 5)) },
                                label = { Text(stringResource(R.string.schedule_weekdays)) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(
                            1 to R.string.day_m,
                            2 to R.string.day_t,
                            3 to R.string.day_w,
                            4 to R.string.day_th,
                            5 to R.string.day_f,
                            6 to R.string.day_sa,
                            7 to R.string.day_su,
                        ).forEach { (day, labelRes) ->
                            FilterChip(
                                selected = scheduledDays.isNotEmpty() && day in scheduledDays,
                                onClick = {
                                    val next = if (day in scheduledDays) scheduledDays - day else scheduledDays + day
                                    onDaysChange(next)
                                },
                                label = { Text(stringResource(labelRes)) },
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
                        label = stringResource(R.string.wifi_ssid_label),
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
                        label = { Text(stringResource(R.string.nfc_uid_label)) },
                        placeholder = { Text(stringResource(R.string.nfc_uid_placeholder)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        supportingText = {
                            Text(stringResource(R.string.nfc_tap_instruction))
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
                                if (notificationAppPackage.isEmpty()) stringResource(R.string.tap_to_select_app) else "$notificationAppName ($notificationAppPackage)",
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
                        label = { Text(stringResource(R.string.notification_keyword_filter)) },
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
                        Text(stringResource(R.string.flip_screen_off_label), style = MaterialTheme.typography.bodySmall)
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
                                    stringResource(R.string.light_current_lux),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if (liveAmbientLux != null) "%.0f lx".format(liveAmbientLux) else stringResource(R.string.light_reading_sensor),
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
                                    Text(stringResource(R.string.light_set_target), style = MaterialTheme.typography.labelMedium)
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
                            if (event == TriggerEvent.LIGHT_BELOW) stringResource(R.string.light_trigger_darker) else stringResource(R.string.light_trigger_brighter),
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
                        listOf(
                            5 to R.string.light_preset_dark_room,
                            20 to R.string.light_preset_dim_night,
                            100 to R.string.light_preset_indoor,
                            300 to R.string.light_preset_office,
                            600 to R.string.light_preset_daylight
                        ).forEach { (presetLux, stringRes) ->
                            FilterChip(
                                selected = lightLux == presetLux,
                                onClick = { onLightLuxChange(presetLux) },
                                label = { Text(stringResource(stringRes), style = MaterialTheme.typography.bodySmall) },
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
                TriggerEvent.DEVICE_SHAKE -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.shake_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TriggerEvent.DEVICE_UNLOCKED -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.unlock_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TriggerEvent.SMS_RECEIVED -> {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = smsSenderFilter,
                        onValueChange = onSmsSenderFilterChange,
                        label = { Text(stringResource(R.string.sms_sender_filter_label)) },
                        placeholder = { Text(stringResource(R.string.sms_sender_filter_placeholder)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.sms_match_mode_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SmsMatchMode.entries.forEach { mode ->
                            FilterChip(
                                selected = smsMatchMode == mode,
                                onClick = { onSmsMatchModeChange(mode) },
                                label = { Text(mode.localizedLabel(), style = MaterialTheme.typography.bodySmall) },
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                    if (smsMatchMode != SmsMatchMode.ANY) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = smsKeyword,
                            onValueChange = onSmsKeywordChange,
                            label = {
                                Text(
                                    when (smsMatchMode) {
                                        SmsMatchMode.EQUALS -> stringResource(R.string.sms_exact_text)
                                        SmsMatchMode.STARTS_WITH -> stringResource(R.string.sms_prefix_keyword)
                                        SmsMatchMode.REGEX -> stringResource(R.string.sms_regex_pattern)
                                        else -> stringResource(R.string.sms_keyword_phrase)
                                    }
                                )
                            },
                            placeholder = {
                                Text(
                                    when (smsMatchMode) {
                                        SmsMatchMode.EQUALS -> "e.g. NEREDESIN"
                                        SmsMatchMode.STARTS_WITH -> "e.g. KOD:"
                                        SmsMatchMode.REGEX -> """e.g. \b\d{4,6}\b"""
                                        else -> "e.g. ACIL or Onay Kodu"
                                    }
                                )
                            },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.TextSnippet, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                else -> {
                    // Triggers with no extra configuration
                }
            }
        }
    }
}
