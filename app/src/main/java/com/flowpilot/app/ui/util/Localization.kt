package com.flowpilot.app.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flowpilot.app.R
import com.flowpilot.app.data.model.*

@get:StringRes
val TriggerEvent.labelRes: Int
    get() = when (this) {
        TriggerEvent.APP_OPENED -> R.string.trigger_app_opened
        TriggerEvent.APP_CLOSED -> R.string.trigger_app_closed
        TriggerEvent.CHARGER_CONNECTED -> R.string.trigger_charger_connected
        TriggerEvent.CHARGER_DISCONNECTED -> R.string.trigger_charger_disconnected
        TriggerEvent.BATTERY_BELOW -> R.string.trigger_battery_below
        TriggerEvent.BATTERY_ABOVE -> R.string.trigger_battery_above
        TriggerEvent.SCREEN_ON -> R.string.trigger_screen_on
        TriggerEvent.SCREEN_OFF -> R.string.trigger_screen_off
        TriggerEvent.TIME_SCHEDULE -> R.string.trigger_time_schedule
        TriggerEvent.WIFI_CONNECTED -> R.string.trigger_wifi_connected
        TriggerEvent.WIFI_DISCONNECTED -> R.string.trigger_wifi_disconnected
        TriggerEvent.BLUETOOTH_CONNECTED -> R.string.trigger_bluetooth_connected
        TriggerEvent.BLUETOOTH_DISCONNECTED -> R.string.trigger_bluetooth_disconnected
        TriggerEvent.NFC_TAG_SCANNED -> R.string.trigger_nfc_tag_scanned
        TriggerEvent.NOTIFICATION_RECEIVED -> R.string.trigger_notification_received
        TriggerEvent.CALL_RINGING -> R.string.trigger_call_ringing
        TriggerEvent.CALL_ANSWERED -> R.string.trigger_call_answered
        TriggerEvent.CALL_OUTGOING -> R.string.trigger_call_outgoing
        TriggerEvent.CALL_ENDED -> R.string.trigger_call_ended
        TriggerEvent.DEVICE_FLIPPED_DOWN -> R.string.trigger_device_flipped_down
        TriggerEvent.DEVICE_FLIPPED_UP -> R.string.trigger_device_flipped_up
        TriggerEvent.DEVICE_SHAKE -> R.string.trigger_device_shake
        TriggerEvent.DEVICE_UNLOCKED -> R.string.trigger_device_unlocked
        TriggerEvent.LIGHT_BELOW -> R.string.trigger_light_below
        TriggerEvent.LIGHT_ABOVE -> R.string.trigger_light_above
        TriggerEvent.SMS_RECEIVED -> R.string.trigger_sms_received
    }

@get:StringRes
val ActionType.labelRes: Int
    get() = when (this) {
        ActionType.BLUETOOTH_ON -> R.string.action_bluetooth_on
        ActionType.BLUETOOTH_OFF -> R.string.action_bluetooth_off
        ActionType.NFC_ON -> R.string.action_nfc_on
        ActionType.NFC_OFF -> R.string.action_nfc_off
        ActionType.BATTERY_SAVER_ON -> R.string.action_battery_saver_on
        ActionType.BATTERY_SAVER_OFF -> R.string.action_battery_saver_off
        ActionType.DARK_THEME_ON -> R.string.action_dark_theme_on
        ActionType.DARK_THEME_OFF -> R.string.action_dark_theme_off
        ActionType.AUTO_ROTATE_ON -> R.string.action_auto_rotate_on
        ActionType.AUTO_ROTATE_OFF -> R.string.action_auto_rotate_off
        ActionType.SHOW_NOTIFICATION -> R.string.action_show_notification
        ActionType.DND_ON -> R.string.action_dnd_on
        ActionType.DND_OFF -> R.string.action_dnd_off
        ActionType.VIBRATE -> R.string.action_vibrate
        ActionType.SOUND_PROFILE_NORMAL -> R.string.action_sound_profile_normal
        ActionType.SOUND_PROFILE_VIBRATE -> R.string.action_sound_profile_vibrate
        ActionType.SOUND_PROFILE_SILENT -> R.string.action_sound_profile_silent
        ActionType.PLAY_SOUND -> R.string.action_play_sound
        ActionType.SET_MEDIA_VOLUME -> R.string.action_set_media_volume
        ActionType.SPEAK_TEXT -> R.string.action_speak_text
        ActionType.CREATE_ALARM -> R.string.action_create_alarm
        ActionType.START_TIMER -> R.string.action_start_timer
        ActionType.LAUNCH_APP -> R.string.action_launch_app
        ActionType.OPEN_URL -> R.string.action_open_url
        ActionType.HTTP_WEBHOOK -> R.string.action_http_webhook
        ActionType.OPEN_DIALER -> R.string.action_open_dialer
        ActionType.DIAL_NUMBER -> R.string.action_dial_number
        ActionType.CALL_NUMBER -> R.string.action_call_number
        ActionType.SEND_SMS -> R.string.action_send_sms
        ActionType.DRAFT_SMS -> R.string.action_draft_sms
        ActionType.MOBILE_DATA_ON -> R.string.action_mobile_data_on
        ActionType.MOBILE_DATA_OFF -> R.string.action_mobile_data_off
        ActionType.WIFI_ON -> R.string.action_wifi_on
        ActionType.WIFI_OFF -> R.string.action_wifi_off
        ActionType.AIRPLANE_MODE_ON -> R.string.action_airplane_mode_on
        ActionType.AIRPLANE_MODE_OFF -> R.string.action_airplane_mode_off
        ActionType.TORCH_ON -> R.string.action_torch_on
        ActionType.TORCH_OFF -> R.string.action_torch_off
        ActionType.SET_SCREEN_BRIGHTNESS -> R.string.action_set_screen_brightness
        ActionType.LOCK_SCREEN -> R.string.action_lock_screen
        ActionType.FORCE_STOP_APP -> R.string.action_force_stop_app
        ActionType.LOCATION_ON -> R.string.action_location_on
        ActionType.LOCATION_OFF -> R.string.action_location_off
    }

@get:StringRes
val ConditionType.labelRes: Int
    get() = when (this) {
        ConditionType.BATTERY_BELOW -> R.string.cond_battery_below
        ConditionType.BATTERY_ABOVE -> R.string.cond_battery_above
        ConditionType.CHARGER_CONNECTED -> R.string.cond_charger_connected
        ConditionType.CHARGER_DISCONNECTED -> R.string.cond_charger_disconnected
        ConditionType.SCREEN_ON -> R.string.cond_screen_on
        ConditionType.SCREEN_OFF -> R.string.cond_screen_off
        ConditionType.WIFI_CONNECTED -> R.string.cond_wifi_connected
        ConditionType.WIFI_DISCONNECTED -> R.string.cond_wifi_disconnected
        ConditionType.TIME_BETWEEN -> R.string.cond_time_between
        ConditionType.DAYS_OF_WEEK -> R.string.cond_days_of_week
    }

@get:StringRes
val TriggerCategory.labelRes: Int
    get() = when (this) {
        TriggerCategory.APP -> R.string.cat_app
        TriggerCategory.POWER -> R.string.cat_power
        TriggerCategory.DISPLAY -> R.string.cat_display
        TriggerCategory.TIME -> R.string.cat_time
        TriggerCategory.NETWORK -> R.string.cat_network
        TriggerCategory.BLUETOOTH -> R.string.cat_bluetooth
        TriggerCategory.NFC_TAG -> R.string.cat_nfc
        TriggerCategory.NOTIFICATION -> R.string.cat_notification
        TriggerCategory.PHONE -> R.string.cat_phone
        TriggerCategory.SMS -> R.string.cat_sms
        TriggerCategory.MOTION -> R.string.cat_motion
    }

@get:StringRes
val ActionCategory.labelRes: Int
    get() = when (this) {
        ActionCategory.CONNECTIVITY -> R.string.cat_connectivity
        ActionCategory.NFC -> R.string.cat_nfc
        ActionCategory.BATTERY -> R.string.cat_battery
        ActionCategory.DISPLAY -> R.string.cat_display
        ActionCategory.ALERTS -> R.string.cat_alerts
        ActionCategory.AUDIO -> R.string.cat_audio
        ActionCategory.APPS_LINKS -> R.string.cat_apps_links
        ActionCategory.CLOCK -> R.string.cat_clock
        ActionCategory.PHONE -> R.string.cat_phone
        ActionCategory.SMS -> R.string.cat_sms
    }

@get:StringRes
val PresetCategory.labelRes: Int
    get() = when (this) {
        PresetCategory.ROUTINE -> R.string.preset_cat_routine
        PresetCategory.BATTERY -> R.string.preset_cat_battery
        PresetCategory.GESTURES -> R.string.preset_cat_gestures
        PresetCategory.SAFETY_LOCATION -> R.string.preset_cat_safety
        PresetCategory.CONNECTIVITY -> R.string.preset_cat_connectivity
    }

@get:StringRes
val SmsMatchMode.labelRes: Int
    get() = when (this) {
        SmsMatchMode.CONTAINS -> R.string.sms_mode_contains
        SmsMatchMode.EQUALS -> R.string.sms_mode_equals
        SmsMatchMode.STARTS_WITH -> R.string.sms_mode_starts_with
        SmsMatchMode.REGEX -> R.string.sms_mode_regex
        SmsMatchMode.ANY -> R.string.sms_mode_any
    }

@get:StringRes
val VibrationPattern.labelRes: Int
    get() = when (this) {
        VibrationPattern.PULSE -> R.string.vib_pulse
        VibrationPattern.DOUBLE_TAP -> R.string.vib_double_tap
        VibrationPattern.ALERT -> R.string.vib_alert
        VibrationPattern.HEARTBEAT -> R.string.vib_heartbeat
        VibrationPattern.TRIPLE_TAP -> R.string.vib_triple_tap
        VibrationPattern.SOS -> R.string.vib_sos
    }

@get:StringRes
val SoundPreset.labelRes: Int
    get() = when (this) {
        SoundPreset.NOTIFICATION -> R.string.sound_preset_notif
        SoundPreset.ALARM -> R.string.sound_preset_alarm
        SoundPreset.RINGTONE -> R.string.sound_preset_ringtone
        SoundPreset.CUSTOM -> R.string.sound_preset_custom
    }

@get:StringRes
val TriggerEvent.descriptionRes: Int
    get() = when (this) {
        TriggerEvent.APP_OPENED -> R.string.trigger_app_opened_desc
        TriggerEvent.APP_CLOSED -> R.string.trigger_app_closed_desc
        TriggerEvent.CHARGER_CONNECTED -> R.string.trigger_charger_connected_desc
        TriggerEvent.CHARGER_DISCONNECTED -> R.string.trigger_charger_disconnected_desc
        TriggerEvent.BATTERY_BELOW -> R.string.trigger_battery_below_desc
        TriggerEvent.BATTERY_ABOVE -> R.string.trigger_battery_above_desc
        TriggerEvent.SCREEN_ON -> R.string.trigger_screen_on_desc
        TriggerEvent.SCREEN_OFF -> R.string.trigger_screen_off_desc
        TriggerEvent.TIME_SCHEDULE -> R.string.trigger_time_schedule_desc
        TriggerEvent.WIFI_CONNECTED -> R.string.trigger_wifi_connected_desc
        TriggerEvent.WIFI_DISCONNECTED -> R.string.trigger_wifi_disconnected_desc
        TriggerEvent.BLUETOOTH_CONNECTED -> R.string.trigger_bluetooth_connected_desc
        TriggerEvent.BLUETOOTH_DISCONNECTED -> R.string.trigger_bluetooth_disconnected_desc
        TriggerEvent.NFC_TAG_SCANNED -> R.string.trigger_nfc_tag_scanned_desc
        TriggerEvent.NOTIFICATION_RECEIVED -> R.string.trigger_notification_received_desc
        TriggerEvent.CALL_RINGING -> R.string.trigger_call_ringing_desc
        TriggerEvent.CALL_ANSWERED -> R.string.trigger_call_answered_desc
        TriggerEvent.CALL_OUTGOING -> R.string.trigger_call_outgoing_desc
        TriggerEvent.CALL_ENDED -> R.string.trigger_call_ended_desc
        TriggerEvent.DEVICE_FLIPPED_DOWN -> R.string.trigger_device_flipped_down_desc
        TriggerEvent.DEVICE_FLIPPED_UP -> R.string.trigger_device_flipped_up_desc
        TriggerEvent.DEVICE_SHAKE -> R.string.trigger_device_shake_desc
        TriggerEvent.DEVICE_UNLOCKED -> R.string.trigger_device_unlocked_desc
        TriggerEvent.LIGHT_BELOW -> R.string.trigger_light_below_desc
        TriggerEvent.LIGHT_ABOVE -> R.string.trigger_light_above_desc
        TriggerEvent.SMS_RECEIVED -> R.string.trigger_sms_received_desc
    }

@get:StringRes
val ActionType.descriptionRes: Int
    get() = when (this) {
        ActionType.BLUETOOTH_ON -> R.string.action_bluetooth_on_desc
        ActionType.BLUETOOTH_OFF -> R.string.action_bluetooth_off_desc
        ActionType.NFC_ON -> R.string.action_nfc_on_desc
        ActionType.NFC_OFF -> R.string.action_nfc_off_desc
        ActionType.BATTERY_SAVER_ON -> R.string.action_battery_saver_on_desc
        ActionType.BATTERY_SAVER_OFF -> R.string.action_battery_saver_off_desc
        ActionType.DARK_THEME_ON -> R.string.action_dark_theme_on_desc
        ActionType.DARK_THEME_OFF -> R.string.action_dark_theme_off_desc
        ActionType.AUTO_ROTATE_ON -> R.string.action_auto_rotate_on_desc
        ActionType.AUTO_ROTATE_OFF -> R.string.action_auto_rotate_off_desc
        ActionType.SHOW_NOTIFICATION -> R.string.action_show_notification_desc
        ActionType.DND_ON -> R.string.action_dnd_on_desc
        ActionType.DND_OFF -> R.string.action_dnd_off_desc
        ActionType.VIBRATE -> R.string.action_vibrate_desc
        ActionType.SOUND_PROFILE_NORMAL -> R.string.action_sound_profile_normal_desc
        ActionType.SOUND_PROFILE_VIBRATE -> R.string.action_sound_profile_vibrate_desc
        ActionType.SOUND_PROFILE_SILENT -> R.string.action_sound_profile_silent_desc
        ActionType.PLAY_SOUND -> R.string.action_play_sound_desc
        ActionType.SET_MEDIA_VOLUME -> R.string.action_set_media_volume_desc
        ActionType.SPEAK_TEXT -> R.string.action_speak_text_desc
        ActionType.CREATE_ALARM -> R.string.action_create_alarm_desc
        ActionType.START_TIMER -> R.string.action_start_timer_desc
        ActionType.LAUNCH_APP -> R.string.action_launch_app_desc
        ActionType.OPEN_URL -> R.string.action_open_url_desc
        ActionType.HTTP_WEBHOOK -> R.string.action_http_webhook_desc
        ActionType.OPEN_DIALER -> R.string.action_open_dialer_desc
        ActionType.DIAL_NUMBER -> R.string.action_dial_number_desc
        ActionType.CALL_NUMBER -> R.string.action_call_number_desc
        ActionType.SEND_SMS -> R.string.action_send_sms_desc
        ActionType.DRAFT_SMS -> R.string.action_draft_sms_desc
        ActionType.MOBILE_DATA_ON -> R.string.action_mobile_data_on_desc
        ActionType.MOBILE_DATA_OFF -> R.string.action_mobile_data_off_desc
        ActionType.WIFI_ON -> R.string.action_wifi_on_desc
        ActionType.WIFI_OFF -> R.string.action_wifi_off_desc
        ActionType.AIRPLANE_MODE_ON -> R.string.action_airplane_mode_on_desc
        ActionType.AIRPLANE_MODE_OFF -> R.string.action_airplane_mode_off_desc
        ActionType.TORCH_ON -> R.string.action_torch_on_desc
        ActionType.TORCH_OFF -> R.string.action_torch_off_desc
        ActionType.SET_SCREEN_BRIGHTNESS -> R.string.action_set_screen_brightness_desc
        ActionType.LOCK_SCREEN -> R.string.action_lock_screen_desc
        ActionType.FORCE_STOP_APP -> R.string.action_force_stop_app_desc
        ActionType.LOCATION_ON -> R.string.action_location_on_desc
        ActionType.LOCATION_OFF -> R.string.action_location_off_desc
    }

@get:StringRes
val ConditionType.descriptionRes: Int
    get() = when (this) {
        ConditionType.BATTERY_BELOW -> R.string.cond_battery_below_desc
        ConditionType.BATTERY_ABOVE -> R.string.cond_battery_above_desc
        ConditionType.CHARGER_CONNECTED -> R.string.cond_charger_connected_desc
        ConditionType.CHARGER_DISCONNECTED -> R.string.cond_charger_disconnected_desc
        ConditionType.SCREEN_ON -> R.string.cond_screen_on_desc
        ConditionType.SCREEN_OFF -> R.string.cond_screen_off_desc
        ConditionType.WIFI_CONNECTED -> R.string.cond_wifi_connected_desc
        ConditionType.WIFI_DISCONNECTED -> R.string.cond_wifi_disconnected_desc
        ConditionType.TIME_BETWEEN -> R.string.cond_time_between_desc
        ConditionType.DAYS_OF_WEEK -> R.string.cond_days_of_week_desc
    }

@get:StringRes
val com.flowpilot.app.permission.CapabilityStatus.labelRes: Int
    get() = when (this) {
        com.flowpilot.app.permission.CapabilityStatus.AVAILABLE -> R.string.capability_available
        com.flowpilot.app.permission.CapabilityStatus.PERMISSION_REQUIRED -> R.string.capability_permission_required
        com.flowpilot.app.permission.CapabilityStatus.SHIZUKU_REQUIRED -> R.string.capability_shizuku_required
        com.flowpilot.app.permission.CapabilityStatus.UNSUPPORTED -> R.string.capability_unsupported
    }

@get:StringRes
val ExecutionStatus.labelRes: Int
    get() = when (this) {
        ExecutionStatus.SUCCESS -> R.string.status_success
        ExecutionStatus.PARTIAL -> R.string.status_partial
        ExecutionStatus.FAILURE -> R.string.status_failed
    }

@Composable
fun TriggerEvent.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun TriggerEvent.localizedDescription(): String = stringResource(this.descriptionRes)

@Composable
fun ActionType.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun ActionType.localizedDescription(): String = stringResource(this.descriptionRes)

@Composable
fun ConditionType.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun ConditionType.localizedDescription(): String = stringResource(this.descriptionRes)

@Composable
fun TriggerCategory.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun ActionCategory.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun PresetCategory.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun SmsMatchMode.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun VibrationPattern.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun SoundPreset.localizedLabel(): String = stringResource(this.labelRes)

@Composable
fun Automation.localizedActionSummary(): String {
    val delays = effectiveActionDelays
    return effectiveActions.mapIndexed { idx, act ->
        val delaySec = delays.getOrElse(idx) { 0 }
        val label = act.localizedLabel()
        if (delaySec > 0) "$label (+${delaySec}s)" else label
    }.joinToString(" + ")
}

class LocalizedContextWrapper(
    base: android.content.Context,
    private val localizedConfig: android.content.res.Configuration,
) : android.content.ContextWrapper(base),
    androidx.activity.result.ActivityResultRegistryOwner,
    androidx.activity.OnBackPressedDispatcherOwner,
    androidx.lifecycle.LifecycleOwner,
    androidx.savedstate.SavedStateRegistryOwner {

    private val localizedContext by lazy {
        base.createConfigurationContext(localizedConfig)
    }

    override fun getResources(): android.content.res.Resources {
        return localizedContext.resources
    }

    override val activityResultRegistry: androidx.activity.result.ActivityResultRegistry
        get() = (baseContext as? androidx.activity.result.ActivityResultRegistryOwner)?.activityResultRegistry
            ?: (baseContext.applicationContext as? androidx.activity.result.ActivityResultRegistryOwner)?.activityResultRegistry
            ?: error("No ActivityResultRegistry found in base context")

    override val onBackPressedDispatcher: androidx.activity.OnBackPressedDispatcher
        get() = (baseContext as? androidx.activity.OnBackPressedDispatcherOwner)?.onBackPressedDispatcher
            ?: error("No OnBackPressedDispatcher found in base context")

    override val lifecycle: androidx.lifecycle.Lifecycle
        get() = (baseContext as? androidx.lifecycle.LifecycleOwner)?.lifecycle
            ?: error("No Lifecycle found in base context")

    override val savedStateRegistry: androidx.savedstate.SavedStateRegistry
        get() = (baseContext as? androidx.savedstate.SavedStateRegistryOwner)?.savedStateRegistry
            ?: error("No SavedStateRegistry found in base context")
}

@Composable
fun AppLocaleProvider(
    language: String,
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val activityResultRegistryOwner = androidx.activity.compose.LocalActivityResultRegistryOwner.current
        ?: (context as? androidx.activity.result.ActivityResultRegistryOwner)

    val targetLocale = androidx.compose.runtime.remember(language) {
        when (language.lowercase()) {
            "tr" -> java.util.Locale("tr")
            "en" -> java.util.Locale("en")
            else -> java.util.Locale.getDefault()
        }
    }

    val localizedConfiguration = androidx.compose.runtime.remember(configuration, targetLocale) {
        android.content.res.Configuration(configuration).apply {
            setLocale(targetLocale)
        }
    }

    val localizedContext = androidx.compose.runtime.remember(context, targetLocale) {
        LocalizedContextWrapper(context, localizedConfiguration)
    }

    androidx.compose.runtime.LaunchedEffect(language) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            val desiredList = when (language.lowercase()) {
                "tr" -> android.os.LocaleList.forLanguageTags("tr")
                "en" -> android.os.LocaleList.forLanguageTags("en")
                else -> android.os.LocaleList.getEmptyLocaleList()
            }
            try {
                if (localeManager != null && localeManager.applicationLocales != desiredList) {
                    localeManager.applicationLocales = desiredList
                }
            } catch (_: Throwable) {}
        }
    }

    if (activityResultRegistryOwner != null) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration,
            androidx.compose.ui.platform.LocalContext provides localizedContext,
            androidx.activity.compose.LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
            content = content,
        )
    } else {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration,
            androidx.compose.ui.platform.LocalContext provides localizedContext,
            content = content,
        )
    }
}

