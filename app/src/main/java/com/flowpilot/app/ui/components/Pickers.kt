@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui.components

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.DoNotDisturbOff
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.flowpilot.app.R
import com.flowpilot.app.data.model.*
import com.flowpilot.app.ui.util.labelRes
import com.flowpilot.app.ui.util.descriptionRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Item model for visual presentation in category-based picker dialogs. */
data class PickerItem<T>(
    val value: T,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val keywords: List<String> = emptyList(),
)

/** Visual category group with icon, label, description, and list of options. */
data class PickerCategoryGroup<T>(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val iconTint: Color? = null,
    val items: List<PickerItem<T>>,
)

fun <T> PickerItem<T>.displayTitle(context: Context): String = when (val v = value) {
    is TriggerEvent -> context.getString(v.labelRes)
    is ActionType -> context.getString(v.labelRes)
    is ConditionType -> context.getString(v.labelRes)
    else -> title
}

fun <T> PickerItem<T>.displaySubtitle(context: Context): String = when (val v = value) {
    is TriggerEvent -> context.getString(v.descriptionRes)
    is ActionType -> context.getString(v.descriptionRes)
    is ConditionType -> context.getString(v.descriptionRes)
    else -> subtitle
}

fun <T> PickerCategoryGroup<T>.displayLabel(context: Context): String {
    val catName = id
    val trCategory = TriggerCategory.entries.firstOrNull { it.name == catName }
    if (trCategory != null) return context.getString(trCategory.labelRes)
    val actCategory = ActionCategory.entries.firstOrNull { it.name == catName }
    if (actCategory != null) return context.getString(actCategory.labelRes)
    return when (catName) {
        "TIME" -> context.getString(R.string.cat_time)
        "POWER" -> context.getString(R.string.cat_power)
        "DISPLAY" -> context.getString(R.string.cat_display)
        "NETWORK" -> context.getString(R.string.cat_network)
        else -> label
    }
}

/** Full-screen modal picker for triggers, grouped by category with search and icons. */
@Composable
fun TriggerPicker(selected: TriggerEvent, select: (TriggerEvent) -> Unit, onDismiss: () -> Unit) {
    val groups = remember {
        listOf(
            PickerCategoryGroup(
                id = TriggerCategory.APP.name,
                label = TriggerCategory.APP.label,
                icon = Icons.Default.Apps,
                iconTint = Color(0xFF8AB4F8),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.APP_OPENED,
                        title = TriggerEvent.APP_OPENED.label,
                        subtitle = "When selected foreground app starts",
                        icon = Icons.Default.Apps,
                        iconTint = Color(0xFF8AB4F8),
                        keywords = listOf("launch", "open", "start", "foreground", "package", "application"),
                    ),
                    PickerItem(
                        value = TriggerEvent.APP_CLOSED,
                        title = TriggerEvent.APP_CLOSED.label,
                        subtitle = "When selected foreground app exits",
                        icon = Icons.Default.Close,
                        iconTint = Color(0xFFF28B82),
                        keywords = listOf("exit", "close", "leave", "background", "quit", "application"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.PHONE.name,
                label = TriggerCategory.PHONE.label,
                icon = Icons.Default.PhoneInTalk,
                iconTint = Color(0xFF81C784),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.CALL_RINGING,
                        title = TriggerEvent.CALL_RINGING.label,
                        subtitle = "When an incoming call starts ringing",
                        icon = Icons.Default.PhoneInTalk,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("phone", "call", "incoming", "ringing", "ring", "caller", "telephony"),
                    ),
                    PickerItem(
                        value = TriggerEvent.CALL_ANSWERED,
                        title = TriggerEvent.CALL_ANSWERED.label,
                        subtitle = "When an incoming call is picked up (off-hook)",
                        icon = Icons.Default.Phone,
                        iconTint = Color(0xFF66BB6A),
                        keywords = listOf("phone", "call", "answered", "answer", "offhook", "pickup", "in-call"),
                    ),
                    PickerItem(
                        value = TriggerEvent.CALL_OUTGOING,
                        title = TriggerEvent.CALL_OUTGOING.label,
                        subtitle = "When an outgoing phone call is placed / dialed",
                        icon = Icons.Default.PhoneForwarded,
                        iconTint = Color(0xFF4CAF50),
                        keywords = listOf("phone", "call", "outgoing", "dialed", "dial", "place", "started"),
                    ),
                    PickerItem(
                        value = TriggerEvent.CALL_ENDED,
                        title = TriggerEvent.CALL_ENDED.label,
                        subtitle = "When an active or ringing call ends (returns to idle)",
                        icon = Icons.Default.CallEnd,
                        iconTint = Color(0xFFE57373),
                        keywords = listOf("phone", "call", "ended", "hangup", "idle", "disconnect", "finished"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.POWER.name,
                label = TriggerCategory.POWER.label,
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFFFBC65E),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.CHARGER_CONNECTED,
                        title = TriggerEvent.CHARGER_CONNECTED.label,
                        subtitle = "When device is plugged into AC or USB charger",
                        icon = Icons.Default.Power,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("charger", "plug", "charging", "connected", "power", "ac", "usb"),
                    ),
                    PickerItem(
                        value = TriggerEvent.CHARGER_DISCONNECTED,
                        title = TriggerEvent.CHARGER_DISCONNECTED.label,
                        subtitle = "When device is unplugged from power source",
                        icon = Icons.Default.PowerOff,
                        iconTint = Color(0xFFFFB74D),
                        keywords = listOf("charger", "unplug", "disconnected", "battery", "off"),
                    ),
                    PickerItem(
                        value = TriggerEvent.BATTERY_BELOW,
                        title = TriggerEvent.BATTERY_BELOW.label,
                        subtitle = "When battery drops to or below configured percentage",
                        icon = Icons.Default.BatteryAlert,
                        iconTint = Color(0xFFE57373),
                        keywords = listOf("battery", "level", "percentage", "low", "drain", "below", "percent"),
                    ),
                    PickerItem(
                        value = TriggerEvent.BATTERY_ABOVE,
                        title = TriggerEvent.BATTERY_ABOVE.label,
                        subtitle = "When battery charges to or above configured percentage",
                        icon = Icons.Default.BatteryFull,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("battery", "level", "percentage", "high", "full", "above", "percent"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.BLUETOOTH.name,
                label = TriggerCategory.BLUETOOTH.label,
                icon = Icons.Default.Bluetooth,
                iconTint = Color(0xFF80CBC4),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.BLUETOOTH_CONNECTED,
                        title = TriggerEvent.BLUETOOTH_CONNECTED.label,
                        subtitle = "When a specific paired Bluetooth device connects",
                        icon = Icons.Default.Bluetooth,
                        iconTint = Color(0xFF80CBC4),
                        keywords = listOf("bluetooth", "bt", "device", "connect", "paired", "headphones", "speaker", "car"),
                    ),
                    PickerItem(
                        value = TriggerEvent.BLUETOOTH_DISCONNECTED,
                        title = TriggerEvent.BLUETOOTH_DISCONNECTED.label,
                        subtitle = "When a specific paired Bluetooth device disconnects",
                        icon = Icons.Default.BluetoothDisabled,
                        iconTint = Color(0xFFB2DFDB),
                        keywords = listOf("bluetooth", "bt", "device", "disconnect", "unpair", "leave", "off"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.NFC_TAG.name,
                label = TriggerCategory.NFC_TAG.label,
                icon = Icons.Default.Nfc,
                iconTint = Color(0xFFB39DDB),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.NFC_TAG_SCANNED,
                        title = TriggerEvent.NFC_TAG_SCANNED.label,
                        subtitle = "When a physical NFC tag is scanned against the phone",
                        icon = Icons.Default.Nfc,
                        iconTint = Color(0xFFB39DDB),
                        keywords = listOf("nfc", "tag", "scan", "rfid", "contactless", "tap", "card"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.DISPLAY.name,
                label = TriggerCategory.DISPLAY.label,
                icon = Icons.Default.ScreenRotation,
                iconTint = Color(0xFF4DD0E1),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.SCREEN_ON,
                        title = TriggerEvent.SCREEN_ON.label,
                        subtitle = "When screen turns on or device wakes up",
                        icon = Icons.Default.StayCurrentPortrait,
                        iconTint = Color(0xFF4DD0E1),
                        keywords = listOf("screen", "display", "wake", "unlock", "on", "light"),
                    ),
                    PickerItem(
                        value = TriggerEvent.SCREEN_OFF,
                        title = TriggerEvent.SCREEN_OFF.label,
                        subtitle = "When screen locks or turns off",
                        icon = Icons.Default.StayCurrentLandscape,
                        iconTint = Color(0xFF80DEEA),
                        keywords = listOf("screen", "display", "sleep", "lock", "off", "dim"),
                    ),
                    PickerItem(
                        value = TriggerEvent.DEVICE_UNLOCKED,
                        title = TriggerEvent.DEVICE_UNLOCKED.label,
                        subtitle = "When device is unlocked with PIN, fingerprint or face",
                        icon = Icons.Default.LockOpen,
                        iconTint = Color(0xFF80CBC4),
                        keywords = listOf("unlock", "unlocked", "lockscreen", "fingerprint", "pin", "biometric", "face"),
                    ),
                    PickerItem(
                        value = TriggerEvent.LIGHT_BELOW,
                        title = TriggerEvent.LIGHT_BELOW.label,
                        subtitle = "When ambient light falls to or below configured lux",
                        icon = Icons.Default.LightMode,
                        iconTint = Color(0xFFFFD54F),
                        keywords = listOf("light", "dark", "lux", "ambient", "sensor", "below", "dim"),
                    ),
                    PickerItem(
                        value = TriggerEvent.LIGHT_ABOVE,
                        title = TriggerEvent.LIGHT_ABOVE.label,
                        subtitle = "When ambient light rises to or above configured lux",
                        icon = Icons.Default.LightMode,
                        iconTint = Color(0xFFFFB74D),
                        keywords = listOf("light", "bright", "lux", "ambient", "sensor", "above", "sun"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.TIME.name,
                label = TriggerCategory.TIME.label,
                icon = Icons.Default.Schedule,
                iconTint = Color(0xFFCE93D8),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.TIME_SCHEDULE,
                        title = TriggerEvent.TIME_SCHEDULE.label,
                        subtitle = "At specific time of day and selected days",
                        icon = Icons.Default.Schedule,
                        iconTint = Color(0xFFCE93D8),
                        keywords = listOf("time", "clock", "alarm", "schedule", "daily", "days", "cron", "minute"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.NETWORK.name,
                label = TriggerCategory.NETWORK.label,
                icon = Icons.Default.Wifi,
                iconTint = Color(0xFF4FC3F7),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.WIFI_CONNECTED,
                        title = TriggerEvent.WIFI_CONNECTED.label,
                        subtitle = "When device connects to a configured Wi-Fi SSID",
                        icon = Icons.Default.Wifi,
                        iconTint = Color(0xFF4FC3F7),
                        keywords = listOf("wifi", "wi-fi", "wireless", "network", "connect", "ssid", "wlan", "internet"),
                    ),
                    PickerItem(
                        value = TriggerEvent.WIFI_DISCONNECTED,
                        title = TriggerEvent.WIFI_DISCONNECTED.label,
                        subtitle = "When device disconnects from a configured Wi-Fi SSID",
                        icon = Icons.Default.WifiOff,
                        iconTint = Color(0xFF81D4FA),
                        keywords = listOf("wifi", "wi-fi", "wireless", "network", "disconnect", "ssid", "wlan", "leave"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.NOTIFICATION.name,
                label = TriggerCategory.NOTIFICATION.label,
                icon = Icons.Default.NotificationsActive,
                iconTint = Color(0xFFFFB74D),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.NOTIFICATION_RECEIVED,
                        title = TriggerEvent.NOTIFICATION_RECEIVED.label,
                        subtitle = "When a notification arrives from selected app (optional keyword)",
                        icon = Icons.Default.NotificationsActive,
                        iconTint = Color(0xFFFFB74D),
                        keywords = listOf("notification", "alert", "message", "push", "keyword", "title", "text", "received"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.MOTION.name,
                label = TriggerCategory.MOTION.label,
                icon = Icons.Default.ScreenRotation,
                iconTint = Color(0xFFFF8A65),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.DEVICE_FLIPPED_DOWN,
                        title = TriggerEvent.DEVICE_FLIPPED_DOWN.label,
                        subtitle = "When device is placed face down on a table or surface",
                        icon = Icons.Default.StayCurrentLandscape,
                        iconTint = Color(0xFFFF8A65),
                        keywords = listOf("flip", "face down", "down", "sensor", "motion", "dnd", "mute", "table", "surface"),
                    ),
                    PickerItem(
                        value = TriggerEvent.DEVICE_FLIPPED_UP,
                        title = TriggerEvent.DEVICE_FLIPPED_UP.label,
                        subtitle = "When device is turned face up back to normal position",
                        icon = Icons.Default.StayCurrentPortrait,
                        iconTint = Color(0xFFFFAB91),
                        keywords = listOf("flip", "face up", "up", "sensor", "motion", "normal", "lift"),
                    ),
                    PickerItem(
                        value = TriggerEvent.DEVICE_SHAKE,
                        title = TriggerEvent.DEVICE_SHAKE.label,
                        subtitle = "When device is firmly shaken back and forth",
                        icon = Icons.Default.Vibration,
                        iconTint = Color(0xFFFF7043),
                        keywords = listOf("shake", "motion", "accelerometer", "gesture", "movement", "vibrate"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.SMS.name,
                label = TriggerCategory.SMS.label,
                icon = Icons.Default.Sms,
                iconTint = Color(0xFF4FC3F7),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.SMS_RECEIVED,
                        title = TriggerEvent.SMS_RECEIVED.label,
                        subtitle = "When an incoming SMS text message is received (filter sender & content)",
                        icon = Icons.Default.Sms,
                        iconTint = Color(0xFF4FC3F7),
                        keywords = listOf("sms", "text", "message", "sender", "incoming", "received", "code", "otp"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = TriggerCategory.PHONE.name,
                label = TriggerCategory.PHONE.label,
                icon = Icons.Default.Phone,
                iconTint = Color(0xFF81C784),
                items = listOf(
                    PickerItem(
                        value = TriggerEvent.CALL_RINGING,
                        title = TriggerEvent.CALL_RINGING.label,
                        subtitle = "When an incoming phone call starts ringing",
                        icon = Icons.Default.PhoneInTalk,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("phone", "call", "ring", "ringing", "incoming call"),
                    ),
                    PickerItem(
                        value = TriggerEvent.CALL_ANSWERED,
                        title = TriggerEvent.CALL_ANSWERED.label,
                        subtitle = "When an incoming or outgoing call is answered / active",
                        icon = Icons.Default.Call,
                        iconTint = Color(0xFF66BB6A),
                        keywords = listOf("phone", "call", "answered", "active", "talk"),
                    ),
                    PickerItem(
                        value = TriggerEvent.CALL_OUTGOING,
                        title = TriggerEvent.CALL_OUTGOING.label,
                        subtitle = "When an outgoing phone call is initiated",
                        icon = Icons.Default.PhoneForwarded,
                        iconTint = Color(0xFF4CAF50),
                        keywords = listOf("phone", "call", "outgoing", "dialed"),
                    ),
                    PickerItem(
                        value = TriggerEvent.CALL_ENDED,
                        title = TriggerEvent.CALL_ENDED.label,
                        subtitle = "When an active or ringing phone call ends or is rejected",
                        icon = Icons.Default.CallEnd,
                        iconTint = Color(0xFFE57373),
                        keywords = listOf("phone", "call", "ended", "hang up", "reject", "finish"),
                    ),
                ),
            ),
        )
    }

    ModernChoiceDialog(
        title = stringResource(R.string.choose_trigger),
        searchPlaceholder = stringResource(R.string.search_triggers_hint),
        groups = groups,
        isSelected = { it == selected },
        onSelect = { select(it) },
        onDismiss = onDismiss,
    )
}

/** Full-screen modal picker for actions, excluding actions already configured on this rule. */
@Composable
fun ActionPicker(
    selected: ActionType?,
    unavailable: Set<ActionType>,
    select: (ActionType) -> Unit,
    onDismiss: () -> Unit,
) {
    val allGroups = remember {
        listOf(
            PickerCategoryGroup(
                id = ActionCategory.SMS.name,
                label = ActionCategory.SMS.label,
                icon = Icons.Default.Sms,
                iconTint = Color(0xFF4FC3F7),
                items = listOf(
                    PickerItem(
                        value = ActionType.SEND_SMS,
                        title = ActionType.SEND_SMS.label,
                        subtitle = "Directly sends an SMS in background (requires SEND_SMS)",
                        icon = Icons.Default.Send,
                        iconTint = Color(0xFF00E676),
                        keywords = listOf("sms", "text", "message", "send", "auto sms", "background"),
                    ),
                    PickerItem(
                        value = ActionType.DRAFT_SMS,
                        title = ActionType.DRAFT_SMS.label,
                        subtitle = "Prepares SMS draft in default SMS app for review",
                        icon = Icons.Default.Drafts,
                        iconTint = Color(0xFF4FC3F7),
                        keywords = listOf("sms", "text", "message", "draft", "prepare", "compose"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.PHONE.name,
                label = ActionCategory.PHONE.label,
                icon = Icons.Default.Phone,
                iconTint = Color(0xFF81C784),
                items = listOf(
                    PickerItem(
                        value = ActionType.OPEN_DIALER,
                        title = ActionType.OPEN_DIALER.label,
                        subtitle = "Open default phone dialer app",
                        icon = Icons.Default.Phone,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("phone", "dialer", "call", "keypad", "open dialer"),
                    ),
                    PickerItem(
                        value = ActionType.DIAL_NUMBER,
                        title = ActionType.DIAL_NUMBER.label,
                        subtitle = "Prepare number in dialer for user confirmation",
                        icon = Icons.Default.PhoneInTalk,
                        iconTint = Color(0xFF66BB6A),
                        keywords = listOf("phone", "dial", "number", "keypad", "prepare", "fill"),
                    ),
                    PickerItem(
                        value = ActionType.CALL_NUMBER,
                        title = ActionType.CALL_NUMBER.label,
                        subtitle = "Directly initiates a phone call automatically (requires CALL_PHONE)",
                        icon = Icons.Default.Call,
                        iconTint = Color(0xFF4CAF50),
                        keywords = listOf("phone", "call", "direct", "call phone", "auto call"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.ALERTS.name,
                label = ActionCategory.ALERTS.label,
                icon = Icons.Default.Notifications,
                iconTint = Color(0xFFFFB74D),
                items = listOf(
                    PickerItem(
                        value = ActionType.SHOW_NOTIFICATION,
                        title = ActionType.SHOW_NOTIFICATION.label,
                        subtitle = "Post a heads-up or status notification",
                        icon = Icons.Default.Notifications,
                        iconTint = Color(0xFFFFB74D),
                        keywords = listOf("notify", "notification", "banner", "message", "alert", "push"),
                    ),
                    PickerItem(
                        value = ActionType.DND_ON,
                        title = ActionType.DND_ON.label,
                        subtitle = "Mute all interruptions and enable Do Not Disturb",
                        icon = Icons.Default.DoNotDisturb,
                        iconTint = Color(0xFFE57373),
                        keywords = listOf("dnd", "do not disturb", "mute", "silence", "quiet", "dnd on", "zen"),
                    ),
                    PickerItem(
                        value = ActionType.DND_OFF,
                        title = ActionType.DND_OFF.label,
                        subtitle = "Allow normal interruptions and disable Do Not Disturb",
                        icon = Icons.Default.DoNotDisturbOff,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("dnd", "do not disturb", "unmute", "normal", "dnd off", "zen off"),
                    ),
                    PickerItem(
                        value = ActionType.VIBRATE,
                        title = ActionType.VIBRATE.label,
                        subtitle = "Vibrate device with custom pattern and strength",
                        icon = Icons.Default.Vibration,
                        iconTint = Color(0xFFFFCC80),
                        keywords = listOf("vibration", "haptic", "buzz", "pulse", "shake", "vibrate"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.CLOCK.name,
                label = ActionCategory.CLOCK.label,
                icon = Icons.Default.Alarm,
                iconTint = Color(0xFFFFD54F),
                items = listOf(
                    PickerItem(
                        value = ActionType.CREATE_ALARM,
                        title = ActionType.CREATE_ALARM.label,
                        subtitle = "Create an alarm in the system Clock app",
                        icon = Icons.Default.Alarm,
                        iconTint = Color(0xFFFFD54F),
                        keywords = listOf("alarm", "clock", "wake", "alert", "time", "set alarm"),
                    ),
                    PickerItem(
                        value = ActionType.START_TIMER,
                        title = ActionType.START_TIMER.label,
                        subtitle = "Start a countdown timer in the system Clock app",
                        icon = Icons.Default.HourglassEmpty,
                        iconTint = Color(0xFFFFCA28),
                        keywords = listOf("timer", "countdown", "clock", "stopwatch", "seconds", "minutes", "start timer"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.AUDIO.name,
                label = ActionCategory.AUDIO.label,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                iconTint = Color(0xFF81D4FA),
                items = listOf(
                    PickerItem(
                        value = ActionType.SOUND_PROFILE_NORMAL,
                        title = ActionType.SOUND_PROFILE_NORMAL.label,
                        subtitle = "Set sound profile to normal (ring and notifications audible)",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconTint = Color(0xFF81D4FA),
                        keywords = listOf("sound", "profile", "ringer", "normal", "ring", "unmute", "audio"),
                    ),
                    PickerItem(
                        value = ActionType.SOUND_PROFILE_VIBRATE,
                        title = ActionType.SOUND_PROFILE_VIBRATE.label,
                        subtitle = "Set sound profile to vibrate (silent with vibration)",
                        icon = Icons.Default.Vibration,
                        iconTint = Color(0xFFFFCC80),
                        keywords = listOf("sound", "profile", "ringer", "vibrate", "vibration", "buzz"),
                    ),
                    PickerItem(
                        value = ActionType.SOUND_PROFILE_SILENT,
                        title = ActionType.SOUND_PROFILE_SILENT.label,
                        subtitle = "Set sound profile to silent (mute calls and notifications)",
                        icon = Icons.Default.DoNotDisturb,
                        iconTint = Color(0xFFE57373),
                        keywords = listOf("sound", "profile", "ringer", "silent", "mute", "silence"),
                    ),
                    PickerItem(
                        value = ActionType.PLAY_SOUND,
                        title = ActionType.PLAY_SOUND.label,
                        subtitle = "Play custom audio file or system tone",
                        icon = Icons.Default.MusicNote,
                        iconTint = Color(0xFF81D4FA),
                        keywords = listOf("audio", "sound", "ringtone", "alarm", "music", "tone", "play"),
                    ),
                    PickerItem(
                        value = ActionType.SET_MEDIA_VOLUME,
                        title = ActionType.SET_MEDIA_VOLUME.label,
                        subtitle = "Adjust media stream volume percentage",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconTint = Color(0xFF4FC3F7),
                        keywords = listOf("volume", "sound", "media", "level", "audio", "loudness", "mute"),
                    ),
                    PickerItem(
                        value = ActionType.SPEAK_TEXT,
                        title = ActionType.SPEAK_TEXT.label,
                        subtitle = "Synthesize and speak voice text with TTS",
                        icon = Icons.Default.RecordVoiceOver,
                        iconTint = Color(0xFF29B6F6),
                        keywords = listOf("tts", "speech", "voice", "talk", "speak", "text to speech", "read"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.APPS_LINKS.name,
                label = ActionCategory.APPS_LINKS.label,
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                iconTint = Color(0xFF80CBC4),
                items = listOf(
                    PickerItem(
                        value = ActionType.LAUNCH_APP,
                        title = ActionType.LAUNCH_APP.label,
                        subtitle = "Start an installed app into the foreground",
                        icon = Icons.Default.Apps,
                        iconTint = Color(0xFF80CBC4),
                        keywords = listOf("launch", "open", "app", "application", "package", "start", "run"),
                    ),
                    PickerItem(
                        value = ActionType.OPEN_URL,
                        title = ActionType.OPEN_URL.label,
                        subtitle = "Open website or deep link in default browser",
                        icon = Icons.Default.Language,
                        iconTint = Color(0xFF4DB6AC),
                        keywords = listOf("url", "link", "web", "browser", "http", "https", "site", "open"),
                    ),
                    PickerItem(
                        value = ActionType.HTTP_WEBHOOK,
                        title = ActionType.HTTP_WEBHOOK.label,
                        subtitle = "Send HTTP request (Home Assistant, Discord, ntfy)",
                        icon = Icons.Default.Http,
                        iconTint = Color(0xFF26A69A),
                        keywords = listOf("webhook", "http", "https", "post", "get", "api", "rest", "ha", "home assistant", "discord", "ntfy", "curl"),
                    ),
                    PickerItem(
                        value = ActionType.FORCE_STOP_APP,
                        title = ActionType.FORCE_STOP_APP.label,
                        subtitle = "Force terminate a running application (requires Shizuku)",
                        icon = Icons.Default.Cancel,
                        iconTint = Color(0xFFEF5350),
                        keywords = listOf("force stop", "kill", "close", "terminate", "app", "application", "shizuku"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.DISPLAY.name,
                label = ActionCategory.DISPLAY.label,
                icon = Icons.Default.ScreenRotation,
                iconTint = Color(0xFF4DD0E1),
                items = listOf(
                    PickerItem(
                        value = ActionType.DARK_THEME_ON,
                        title = ActionType.DARK_THEME_ON.label,
                        subtitle = "Enable system-wide dark theme (requires Shizuku)",
                        icon = Icons.Default.DarkMode,
                        iconTint = Color(0xFF90CAF9),
                        keywords = listOf("dark", "dark mode", "dark theme", "night", "night mode", "uimode", "theme"),
                    ),
                    PickerItem(
                        value = ActionType.DARK_THEME_OFF,
                        title = ActionType.DARK_THEME_OFF.label,
                        subtitle = "Disable dark theme / switch to light mode (requires Shizuku)",
                        icon = Icons.Default.LightMode,
                        iconTint = Color(0xFFFFD54F),
                        keywords = listOf("light", "light mode", "light theme", "day", "day mode", "uimode", "theme"),
                    ),
                    PickerItem(
                        value = ActionType.AUTO_ROTATE_ON,
                        title = ActionType.AUTO_ROTATE_ON.label,
                        subtitle = "Enable automatic screen rotation based on orientation",
                        icon = Icons.Default.ScreenRotation,
                        iconTint = Color(0xFF4DD0E1),
                        keywords = listOf("rotate", "rotation", "auto rotate", "orientation", "landscape", "portrait", "screen"),
                    ),
                    PickerItem(
                        value = ActionType.AUTO_ROTATE_OFF,
                        title = ActionType.AUTO_ROTATE_OFF.label,
                        subtitle = "Lock screen orientation to portrait / disable auto-rotate",
                        icon = Icons.Default.StayCurrentPortrait,
                        iconTint = Color(0xFF80DEEA),
                        keywords = listOf("rotate", "rotation", "lock rotation", "portrait lock", "orientation", "screen lock", "disable auto rotate"),
                    ),
                    PickerItem(
                        value = ActionType.SET_SCREEN_BRIGHTNESS,
                        title = ActionType.SET_SCREEN_BRIGHTNESS.label,
                        subtitle = "Adjust screen brightness level (0% - 100%)",
                        icon = Icons.Default.BrightnessMedium,
                        iconTint = Color(0xFFFFD54F),
                        keywords = listOf("brightness", "screen", "display", "dim", "bright", "level", "percent"),
                    ),
                    PickerItem(
                        value = ActionType.AUTO_BRIGHTNESS_ON,
                        title = ActionType.AUTO_BRIGHTNESS_ON.label,
                        subtitle = "Turn on adaptive / automatic screen brightness",
                        icon = Icons.Default.BrightnessAuto,
                        iconTint = Color(0xFFFFB74D),
                        keywords = listOf("auto brightness", "automatic brightness", "adaptive brightness", "screen", "display", "light"),
                    ),
                    PickerItem(
                        value = ActionType.AUTO_BRIGHTNESS_OFF,
                        title = ActionType.AUTO_BRIGHTNESS_OFF.label,
                        subtitle = "Turn off adaptive brightness / lock to manual level",
                        icon = Icons.Default.BrightnessMedium,
                        iconTint = Color(0xFFFFCC80),
                        keywords = listOf("auto brightness", "manual brightness", "screen", "display", "disable auto brightness"),
                    ),
                    PickerItem(
                        value = ActionType.LOCK_SCREEN,
                        title = ActionType.LOCK_SCREEN.label,
                        subtitle = "Lock screen and turn off display (requires Shizuku)",
                        icon = Icons.Default.Lock,
                        iconTint = Color(0xFFE57373),
                        keywords = listOf("lock", "screen", "display", "power", "turn off", "sleep", "shizuku"),
                    ),
                    PickerItem(
                        value = ActionType.TORCH_ON,
                        title = ActionType.TORCH_ON.label,
                        subtitle = "Turn camera flash on as flashlight",
                        icon = Icons.Default.FlashlightOn,
                        iconTint = Color(0xFFFFD54F),
                        keywords = listOf("flashlight", "torch", "flash", "light", "camera", "on", "enable"),
                    ),
                    PickerItem(
                        value = ActionType.TORCH_OFF,
                        title = ActionType.TORCH_OFF.label,
                        subtitle = "Turn camera flash / flashlight off",
                        icon = Icons.Default.FlashlightOff,
                        iconTint = Color(0xFFFFF59D),
                        keywords = listOf("flashlight", "torch", "flash", "light", "camera", "off", "disable"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.BATTERY.name,
                label = ActionCategory.BATTERY.label,
                icon = Icons.Default.BatterySaver,
                iconTint = Color(0xFFAED581),
                items = listOf(
                    PickerItem(
                        value = ActionType.BATTERY_SAVER_ON,
                        title = ActionType.BATTERY_SAVER_ON.label,
                        subtitle = "Enable Android low-power saver mode",
                        icon = Icons.Default.BatterySaver,
                        iconTint = Color(0xFFAED581),
                        keywords = listOf("battery", "power saver", "battery saver", "save power", "energy", "saver on"),
                    ),
                    PickerItem(
                        value = ActionType.BATTERY_SAVER_OFF,
                        title = ActionType.BATTERY_SAVER_OFF.label,
                        subtitle = "Disable Android low-power saver mode",
                        icon = Icons.Default.Bolt,
                        iconTint = Color(0xFFFFD54F),
                        keywords = listOf("battery", "power saver", "battery saver", "normal power", "energy", "saver off"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.CONNECTIVITY.name,
                label = ActionCategory.CONNECTIVITY.label,
                icon = Icons.Default.Bluetooth,
                iconTint = Color(0xFF80CBC4),
                items = listOf(
                    PickerItem(ActionType.BLUETOOTH_ON, ActionType.BLUETOOTH_ON.label, "Turn Bluetooth radio on (requires Shizuku)", Icons.Default.Bluetooth, Color(0xFF80CBC4), listOf("bluetooth", "radio", "connectivity", "on", "enable", "shizuku")),
                    PickerItem(ActionType.BLUETOOTH_OFF, ActionType.BLUETOOTH_OFF.label, "Turn Bluetooth radio off (requires Shizuku)", Icons.Default.BluetoothDisabled, Color(0xFFB2DFDB), listOf("bluetooth", "radio", "connectivity", "off", "disable", "shizuku")),
                    PickerItem(ActionType.WIFI_ON, ActionType.WIFI_ON.label, "Turn Wi-Fi radio on (requires Shizuku)", Icons.Default.Wifi, Color(0xFF64B5F6), listOf("wifi", "wlan", "internet", "wireless", "on", "enable", "shizuku")),
                    PickerItem(ActionType.WIFI_OFF, ActionType.WIFI_OFF.label, "Turn Wi-Fi radio off (requires Shizuku)", Icons.Default.WifiOff, Color(0xFF90CAF9), listOf("wifi", "wlan", "internet", "wireless", "off", "disable", "shizuku")),
                    PickerItem(ActionType.MOBILE_DATA_ON, ActionType.MOBILE_DATA_ON.label, "Turn mobile cellular data on (requires Shizuku)", Icons.Default.SignalCellularAlt, Color(0xFF81C784), listOf("mobile data", "data", "cellular", "lte", "5g", "internet", "on", "enable", "shizuku")),
                    PickerItem(ActionType.MOBILE_DATA_OFF, ActionType.MOBILE_DATA_OFF.label, "Turn mobile cellular data off (requires Shizuku)", Icons.Default.SignalCellularOff, Color(0xFFA5D6A7), listOf("mobile data", "data", "cellular", "lte", "5g", "internet", "off", "disable", "shizuku")),
                    PickerItem(ActionType.AIRPLANE_MODE_ON, ActionType.AIRPLANE_MODE_ON.label, "Turn Airplane mode on (requires Shizuku)", Icons.Default.AirplanemodeActive, Color(0xFFFFB74D), listOf("airplane", "flight", "mode", "connectivity", "offline", "on", "enable", "shizuku")),
                    PickerItem(ActionType.AIRPLANE_MODE_OFF, ActionType.AIRPLANE_MODE_OFF.label, "Turn Airplane mode off (requires Shizuku)", Icons.Default.AirplanemodeInactive, Color(0xFFFFCC80), listOf("airplane", "flight", "mode", "connectivity", "online", "off", "disable", "shizuku")),
                    PickerItem(ActionType.LOCATION_ON, ActionType.LOCATION_ON.label, "Turn Location / GPS services on (requires Shizuku)", Icons.Default.LocationOn, Color(0xFF81C784), listOf("location", "gps", "position", "geo", "on", "enable", "shizuku")),
                    PickerItem(ActionType.LOCATION_OFF, ActionType.LOCATION_OFF.label, "Turn Location / GPS services off (requires Shizuku)", Icons.Default.LocationOff, Color(0xFFE57373), listOf("location", "gps", "position", "geo", "off", "disable", "shizuku")),
                ),
            ),
            PickerCategoryGroup(
                id = ActionCategory.NFC.name,
                label = ActionCategory.NFC.label,
                icon = Icons.Default.Nfc,
                iconTint = Color(0xFFB39DDB),
                items = listOf(
                    PickerItem(
                        value = ActionType.NFC_ON,
                        title = ActionType.NFC_ON.label,
                        subtitle = "Turn NFC radio on (requires Shizuku)",
                        icon = Icons.Default.Nfc,
                        iconTint = Color(0xFFB39DDB),
                        keywords = listOf("nfc", "contactless", "beam", "tag", "nfc on", "enable"),
                    ),
                    PickerItem(
                        value = ActionType.NFC_OFF,
                        title = ActionType.NFC_OFF.label,
                        subtitle = "Turn NFC radio off (requires Shizuku)",
                        icon = Icons.Default.Nfc,
                        iconTint = Color(0xFF9575CD),
                        keywords = listOf("nfc", "contactless", "beam", "tag", "nfc off", "disable"),
                    ),
                ),
            ),
        )
    }

    val availableGroups = remember(unavailable, selected) {
        allGroups.mapNotNull { group ->
            val filteredItems = group.items.filter { item ->
                item.value == selected || item.value !in unavailable
            }
            if (filteredItems.isNotEmpty()) group.copy(items = filteredItems) else null
        }
    }

    ModernChoiceDialog(
        title = stringResource(R.string.choose_action),
        searchPlaceholder = stringResource(R.string.search_actions_hint),
        groups = availableGroups,
        isSelected = { selected != null && it == selected },
        onSelect = { select(it) },
        onDismiss = onDismiss,
    )
}

/** Full-screen modal picker for conditions, grouped by category with search and icons. */
@Composable
fun ConditionPicker(
    onAdd: (RuleCondition) -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = remember {
        listOf(
            PickerCategoryGroup(
                id = "TIME",
                label = "Time & Schedule",
                icon = Icons.Default.Schedule,
                iconTint = Color(0xFF64B5F6),
                items = listOf(
                    PickerItem(
                        value = ConditionType.TIME_BETWEEN,
                        title = ConditionType.TIME_BETWEEN.label,
                        subtitle = "Run only during a specific time interval (e.g. 23:00 to 07:00)",
                        icon = Icons.Default.Schedule,
                        iconTint = Color(0xFF64B5F6),
                        keywords = listOf("time", "hour", "minute", "between", "window", "night", "overnight", "clock", "saat"),
                    ),
                    PickerItem(
                        value = ConditionType.DAYS_OF_WEEK,
                        title = ConditionType.DAYS_OF_WEEK.label,
                        subtitle = "Run only on selected days (weekdays, weekends, or custom days)",
                        icon = Icons.Default.Tune,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("day", "days", "week", "weekdays", "weekends", "monday", "sunday", "schedule", "gün"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = "POWER",
                label = "Power & Battery",
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFFFFD54F),
                items = listOf(
                    PickerItem(
                        value = ConditionType.BATTERY_BELOW,
                        title = ConditionType.BATTERY_BELOW.label,
                        subtitle = "Run only when battery level is at or below percentage",
                        icon = Icons.Default.BatteryAlert,
                        iconTint = Color(0xFFE57373),
                        keywords = listOf("battery", "level", "percentage", "low", "below", "pil"),
                    ),
                    PickerItem(
                        value = ConditionType.BATTERY_ABOVE,
                        title = ConditionType.BATTERY_ABOVE.label,
                        subtitle = "Run only when battery level is at or above percentage",
                        icon = Icons.Default.BatteryFull,
                        iconTint = Color(0xFF81C784),
                        keywords = listOf("battery", "level", "percentage", "high", "above", "pil"),
                    ),
                    PickerItem(
                        value = ConditionType.CHARGER_CONNECTED,
                        title = ConditionType.CHARGER_CONNECTED.label,
                        subtitle = "Run only while phone is plugged into power",
                        icon = Icons.Default.BatteryChargingFull,
                        iconTint = Color(0xFFFFD54F),
                        keywords = listOf("charger", "plugged", "charging", "power", "connected", "şarj"),
                    ),
                    PickerItem(
                        value = ConditionType.CHARGER_DISCONNECTED,
                        title = ConditionType.CHARGER_DISCONNECTED.label,
                        subtitle = "Run only while phone is unplugged and discharging",
                        icon = Icons.Default.PowerOff,
                        iconTint = Color(0xFFFFB74D),
                        keywords = listOf("charger", "unplugged", "discharging", "battery", "disconnected", "şarj"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = "DISPLAY",
                label = "Display",
                icon = Icons.Default.ScreenRotation,
                iconTint = Color(0xFF4DD0E1),
                items = listOf(
                    PickerItem(
                        value = ConditionType.SCREEN_ON,
                        title = ConditionType.SCREEN_ON.label,
                        subtitle = "Run only while device screen is on or awake",
                        icon = Icons.Default.StayCurrentPortrait,
                        iconTint = Color(0xFF4DD0E1),
                        keywords = listOf("screen", "display", "on", "awake", "unlocked", "ekran"),
                    ),
                    PickerItem(
                        value = ConditionType.SCREEN_OFF,
                        title = ConditionType.SCREEN_OFF.label,
                        subtitle = "Run only while screen is turned off or locked",
                        icon = Icons.Default.StayCurrentLandscape,
                        iconTint = Color(0xFF80DEEA),
                        keywords = listOf("screen", "display", "off", "locked", "sleep", "ekran"),
                    ),
                ),
            ),
            PickerCategoryGroup(
                id = "NETWORK",
                label = "Network",
                icon = Icons.Default.Wifi,
                iconTint = Color(0xFF81D4FA),
                items = listOf(
                    PickerItem(
                        value = ConditionType.WIFI_CONNECTED,
                        title = ConditionType.WIFI_CONNECTED.label,
                        subtitle = "Run only when connected to Wi-Fi (optional specific SSID)",
                        icon = Icons.Default.Wifi,
                        iconTint = Color(0xFF4FC3F7),
                        keywords = listOf("wifi", "wi-fi", "ssid", "wireless", "connected", "network", "wlan"),
                    ),
                    PickerItem(
                        value = ConditionType.WIFI_DISCONNECTED,
                        title = ConditionType.WIFI_DISCONNECTED.label,
                        subtitle = "Run only when disconnected from Wi-Fi or specific SSID",
                        icon = Icons.Default.WifiOff,
                        iconTint = Color(0xFF81D4FA),
                        keywords = listOf("wifi", "wi-fi", "ssid", "disconnected", "off", "network", "wlan"),
                    ),
                ),
            ),
        )
    }

    ModernChoiceDialog(
        title = stringResource(R.string.choose_condition),
        searchPlaceholder = stringResource(R.string.search_conditions_hint),
        groups = groups,
        isSelected = { false },
        onSelect = { type ->
            onAdd(RuleCondition(type = type))
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/** Full-screen modal picker with search, category grouping, colored icons, and rich cards. */
@Composable
fun <T> ModernChoiceDialog(
    title: String,
    searchPlaceholder: String,
    groups: List<PickerCategoryGroup<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredGroups = remember(groups, searchQuery, context) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            groups
        } else {
            groups.mapNotNull { group ->
                val categoryMatches = group.displayLabel(context).lowercase().contains(query)
                val matchingItems = group.items.filter { item ->
                    categoryMatches ||
                        item.displayTitle(context).lowercase().contains(query) ||
                        item.displaySubtitle(context).lowercase().contains(query) ||
                        item.subtitle.lowercase().contains(query) ||
                        item.keywords.any { it.lowercase().contains(query) }
                }
                if (matchingItems.isNotEmpty()) group.copy(items = matchingItems) else null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        val closeDesc = stringResource(R.string.content_desc_close)
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.semantics { contentDescription = closeDesc },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = {
                        Text(
                            searchPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.content_desc_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            val clearDesc = stringResource(R.string.content_desc_clear_search)
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.semantics { contentDescription = clearDesc },
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                )

                Spacer(Modifier.height(8.dp))

                if (filteredGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.no_matches_found),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.no_matches_found_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Compute the position of the selected item in the flattened list (header + items + spacer)
                    val initialScrollIndex = remember(filteredGroups) {
                        var index = 0
                        for (group in filteredGroups) {
                            index++ // header item
                            for (item in group.items) {
                                if (isSelected(item.value)) {
                                    return@remember (index - 1).coerceAtLeast(0) // Show section header right above or target card
                                }
                                index++
                            }
                            index++ // spacer item
                        }
                        0
                    }
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filteredGroups.forEach { group ->
                            item(key = "hdr-${group.id}") {
                                CategorySectionHeader(
                                    title = group.displayLabel(context),
                                    count = group.items.size,
                                    icon = group.icon,
                                    iconTint = group.iconTint ?: MaterialTheme.colorScheme.primary,
                                )
                            }
                            items(
                                items = group.items,
                                key = { "${group.id}-${it.displayTitle(context)}" },
                            ) { item ->
                                val selected = isSelected(item.value)
                                PickerOptionCard(
                                    item = item,
                                    selected = selected,
                                    onClick = { onSelect(item.value) },
                                )
                            }
                            item(key = "spacer-${group.id}") {
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Category header row with colored leading icon, name and item count pill. */
@Composable
private fun CategorySectionHeader(
    title: String,
    count: Int,
    icon: ImageVector,
    iconTint: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = iconTint,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** Compact rounded card representing one picker option with leading icon, title, subtitle, check. */
@Composable
private fun <T> PickerOptionCard(
    item: PickerItem<T>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val itemTitle = item.displayTitle(context)
    val itemSubtitle = item.displaySubtitle(context)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val borderStroke = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                this.role = Role.RadioButton
                this.selected = selected
                this.contentDescription = "$itemTitle, $itemSubtitle${if (selected) ", selected" else ""}"
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconBg = (item.iconTint ?: MaterialTheme.colorScheme.primary).copy(alpha = if (selected) 0.25f else 0.15f)
            val iconColor = item.iconTint ?: MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = itemTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = itemSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionRow(title: String, sub: String, click: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    sub,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class AppDisplayItem(
    val packageName: String,
    val label: String,
    val appInfo: ApplicationInfo,
)

@Composable
fun AppPicker(
    select: (String, String) -> Unit,
    selectedPackage: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppDisplayItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(0)
            val launchable = installed
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { info ->
                    AppDisplayItem(
                        packageName = info.packageName,
                        label = info.loadLabel(pm).toString(),
                        appInfo = info,
                    )
                }
                .sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                apps = launchable
                isLoading = false
            }
        }
    }

    val filtered = remember(query, apps) {
        val list = apps ?: emptyList()
        if (query.isBlank()) list
        else list.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filtered, isLoading) {
        if (!isLoading && !selectedPackage.isNullOrBlank() && query.isBlank()) {
            val targetIndex = filtered.indexOfFirst { it.packageName == selectedPackage }
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.choose_app_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Default.Close, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(stringResource(R.string.search_apps_placeholder)) },
                    singleLine = true,
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Loading applications...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No apps found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(app) { select(app.packageName, app.label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppRow(app: AppDisplayItem, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(app.packageName) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            try {
                val icon = app.appInfo.loadIcon(context.packageManager)
                val bmp = icon.toBitmap().asImageBitmap()
                withContext(Dispatchers.Main) {
                    bitmap = bmp
                }
            } catch (_: Throwable) {}
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(
                Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
