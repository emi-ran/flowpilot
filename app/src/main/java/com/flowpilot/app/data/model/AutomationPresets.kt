package com.flowpilot.app.data.model

import androidx.annotation.StringRes
import com.flowpilot.app.R
import java.util.UUID

/**
 * Predefined automation preset categories for UI filtering.
 */
enum class PresetCategory(val label: String) {
    ROUTINE("Routines"),
    BATTERY("Battery & Power"),
    GESTURES("Sensors & Gestures"),
    SAFETY_LOCATION("Safety & Location"),
    CONNECTIVITY("Network & Connectivity"),
}

/**
 * A ready-to-use automation template that can be applied to the creation form with a single tap.
 */
data class AutomationPreset(
    val id: String,
    val title: String,
    val description: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val category: PresetCategory,
    val iconName: String,
    val template: Automation,
)

object AutomationPresets {

    val all: List<AutomationPreset> = listOf(
        AutomationPreset(
            id = "preset_bedtime",
            title = "Gece / Uyku Rutini",
            description = "Saat 23:30'da koyu temayı açar, Rahatsız Etmeyin'i ve sessiz profili etkinleştirir, ekran parlaklığını %10'a düşürür.",
            titleRes = R.string.preset_bedtime_title,
            descriptionRes = R.string.preset_bedtime_desc,
            category = PresetCategory.ROUTINE,
            iconName = "Bedtime",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Gece / Uyku Rutini",
                triggerEvent = TriggerEvent.TIME_SCHEDULE,
                scheduledMinute = 23 * 60 + 30,
                scheduledDays = setOf(1, 2, 3, 4, 5, 6, 7),
                conditions = listOf(
                    RuleCondition(
                        type = ConditionType.TIME_BETWEEN,
                        startMinute = 23 * 60,
                        endMinute = 7 * 60,
                    )
                ),
                actions = listOf(
                    ActionType.DARK_THEME_ON,
                    ActionType.DND_ON,
                    ActionType.SOUND_PROFILE_SILENT,
                    ActionType.SET_SCREEN_BRIGHTNESS,
                ),
                actionDelays = listOf(0, 0, 0, 0),
                screenBrightnessPercent = 10,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_full_battery",
            title = "Pil %100 Doldu Uyarısı",
            description = "Telefon şarjdayken pil %99 üzerine ulaştığında sesli TTS ile haber verir, bildirim gösterir ve uyarı sesi çalar.",
            titleRes = R.string.preset_full_battery_title,
            descriptionRes = R.string.preset_full_battery_desc,
            category = PresetCategory.BATTERY,
            iconName = "BatteryChargingFull",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Pil %100 Doldu Uyarısı",
                triggerEvent = TriggerEvent.BATTERY_ABOVE,
                batteryLevel = 99,
                conditions = listOf(
                    RuleCondition(type = ConditionType.CHARGER_CONNECTED)
                ),
                actions = listOf(
                    ActionType.SPEAK_TEXT,
                    ActionType.SHOW_NOTIFICATION,
                    ActionType.PLAY_SOUND,
                ),
                actionDelays = listOf(0, 0, 0),
                ttsText = "Pil şarjı tamamlandı, lütfen prizden çekin.",
                notificationTitle = "Şarj Tamamlandı",
                notificationBody = "Pil %100 seviyesine ulaştı. Cihazınızı şarjdan çıkarabilirsiniz.",
                soundPreset = SoundPreset.NOTIFICATION,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_battery_emergency",
            title = "Acil Pil Tasarrufu",
            description = "Pil %15 altına düştüğünde Pil Tasarrufunu açar, ekran parlaklığını %15 yapar, Bluetooth'u kapatır ve koyu temaya geçer.",
            titleRes = R.string.preset_battery_emergency_title,
            descriptionRes = R.string.preset_battery_emergency_desc,
            category = PresetCategory.BATTERY,
            iconName = "BatteryAlert",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Acil Pil Tasarrufu",
                triggerEvent = TriggerEvent.BATTERY_BELOW,
                batteryLevel = 15,
                // Below 15% while charging usually recovers on its own, so only act unplugged.
                // `conditions` stays empty: a reader that ignores the tree keeps the old
                // behaviour instead of applying an inverted flat list.
                conditionTree = ConditionNode.Group(
                    combinator = ConditionCombinator.AND,
                    negate = true,
                    children = listOf(
                        ConditionNode.Leaf(RuleCondition(type = ConditionType.CHARGER_CONNECTED))
                    ),
                ),
                actions = listOf(
                    ActionType.BATTERY_SAVER_ON,
                    ActionType.SET_SCREEN_BRIGHTNESS,
                    ActionType.BLUETOOTH_OFF,
                    ActionType.DARK_THEME_ON,
                ),
                actionDelays = listOf(0, 0, 0, 0),
                screenBrightnessPercent = 15,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_flip_silence",
            title = "Yüzüstü Sessize Al",
            description = "Telefon yüzüstü masaya bırakıldığında Rahatsız Etmeyin modunu açar ve hafif bir onay titreşimi verir.",
            titleRes = R.string.preset_flip_silence_title,
            descriptionRes = R.string.preset_flip_silence_desc,
            category = PresetCategory.GESTURES,
            iconName = "ScreenRotation",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Yüzüstü Sessize Al",
                triggerEvent = TriggerEvent.DEVICE_FLIPPED_DOWN,
                actions = listOf(
                    ActionType.DND_ON,
                    ActionType.VIBRATE,
                ),
                actionDelays = listOf(0, 0),
                vibrationPattern = VibrationPattern.PULSE,
                vibrationDurationMs = 150,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_shake_torch",
            title = "Sallayarak Feneri Aç/Kapat",
            description = "Telefon sallandığında arka kamera fenerini açar veya kapatır ve çift titreşimle geri bildirim verir.",
            titleRes = R.string.preset_shake_torch_title,
            descriptionRes = R.string.preset_shake_torch_desc,
            category = PresetCategory.GESTURES,
            iconName = "FlashlightOn",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Sallayarak Feneri Aç/Kapat",
                triggerEvent = TriggerEvent.DEVICE_SHAKE,
                actions = listOf(
                    ActionType.TORCH_TOGGLE,
                    ActionType.VIBRATE,
                ),
                actionDelays = listOf(0, 0),
                vibrationPattern = VibrationPattern.DOUBLE_TAP,
                vibrationDurationMs = 180,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_cinema_mode",
            title = "Sinema / Gece Okuma Modu",
            description = "Ortam ışığı 5 lüksün altına indiğinde ekran parlaklığını %5'e düşürür ve koyu temayı açar.",
            titleRes = R.string.preset_cinema_title,
            descriptionRes = R.string.preset_cinema_desc,
            category = PresetCategory.ROUTINE,
            iconName = "Nightlife",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Sinema / Gece Okuma Modu",
                triggerEvent = TriggerEvent.LIGHT_BELOW,
                lightLux = 5,
                conditions = listOf(
                    RuleCondition(type = ConditionType.SCREEN_ON)
                ),
                actions = listOf(
                    ActionType.SET_SCREEN_BRIGHTNESS,
                    ActionType.DARK_THEME_ON,
                ),
                actionDelays = listOf(0, 0),
                screenBrightnessPercent = 5,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_leaving_home",
            title = "Evden Çıkış Modu",
            description = "Ev Wi-Fi ağından ayrıldığınızda mobil veriyi açar, ses profilini normale alır ve medya sesini %80 yapar.",
            titleRes = R.string.preset_leaving_home_title,
            descriptionRes = R.string.preset_leaving_home_desc,
            category = PresetCategory.CONNECTIVITY,
            iconName = "ExitToApp",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Evden Çıkış Modu",
                triggerEvent = TriggerEvent.WIFI_DISCONNECTED,
                wifiSsid = "Ev Wi-Fi",
                actions = listOf(
                    ActionType.MOBILE_DATA_ON,
                    ActionType.SOUND_PROFILE_NORMAL,
                    ActionType.SET_MEDIA_VOLUME,
                ),
                actionDelays = listOf(0, 0, 0),
                mediaVolumePercent = 80,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_welcome_home",
            title = "Eve Varış Modu",
            description = "Ev Wi-Fi ağına bağlandığınızda mobil veriyi kapatarak pil tasarrufu sağlar ve ekran parlaklığını %50'ye getirir.",
            titleRes = R.string.preset_welcome_home_title,
            descriptionRes = R.string.preset_welcome_home_desc,
            category = PresetCategory.CONNECTIVITY,
            iconName = "Home",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Eve Varış Modu",
                triggerEvent = TriggerEvent.WIFI_CONNECTED,
                wifiSsid = "Ev Wi-Fi",
                actions = listOf(
                    ActionType.MOBILE_DATA_OFF,
                    ActionType.SET_SCREEN_BRIGHTNESS,
                ),
                actionDelays = listOf(0, 0),
                screenBrightnessPercent = 50,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_sms_location_responder",
            title = "SMS Acil Konum Yanıtı",
            description = "'NEREDESIN' içerikli SMS geldiğinde önce GPS konumunu açar, 5 saniye uydu kilidini bekler ve anlık Google Haritalar konumunu SMS ile geri yollar.",
            titleRes = R.string.preset_sms_location_title,
            descriptionRes = R.string.preset_sms_location_desc,
            category = PresetCategory.SAFETY_LOCATION,
            iconName = "LocationOn",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "SMS Acil Konum Yanıtı",
                triggerEvent = TriggerEvent.SMS_RECEIVED,
                smsKeyword = "NEREDESIN",
                smsMatchMode = SmsMatchMode.CONTAINS,
                actions = listOf(
                    ActionType.LOCATION_ON,
                    ActionType.SEND_SMS,
                ),
                actionDelays = listOf(0, 5),
                smsRecipient = "\${sms.sender}",
                smsMessage = "Şu anki konumum: \${location.maps_url} (Pil: %\${batteryPercent})",
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_wake_up",
            title = "Sabah Uyanışı",
            description = "Hafta içi 05:00-10:00 arasında şarjdan çekince veya ekranı açınca Rahatsız Etmeyin'i kapatır, sesi normale alır ve parlaklığı %70 yapar.",
            titleRes = R.string.preset_wake_up_title,
            descriptionRes = R.string.preset_wake_up_desc,
            category = PresetCategory.ROUTINE,
            iconName = "WbSunny",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Sabah Uyanışı",
                triggerEvent = TriggerEvent.CHARGER_DISCONNECTED,
                triggerDefinitions = TriggerDefinitions(
                    items = listOf(
                        TriggerDefinition.ChargerDisconnected,
                        TriggerDefinition.ScreenOn,
                    )
                ),
                conditions = listOf(
                    RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 5 * 60, endMinute = 10 * 60),
                    RuleCondition(type = ConditionType.DAYS_OF_WEEK, days = setOf(1, 2, 3, 4, 5)),
                ),
                conditionTree = ConditionNode.Group(
                    combinator = ConditionCombinator.AND,
                    children = listOf(
                        ConditionNode.Leaf(
                            RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 5 * 60, endMinute = 10 * 60)
                        ),
                        ConditionNode.Leaf(
                            RuleCondition(type = ConditionType.DAYS_OF_WEEK, days = setOf(1, 2, 3, 4, 5))
                        ),
                    ),
                ),
                actions = listOf(
                    ActionType.DND_OFF,
                    ActionType.SOUND_PROFILE_NORMAL,
                    ActionType.SET_SCREEN_BRIGHTNESS,
                ),
                actionDelays = listOf(0, 0, 0),
                screenBrightnessPercent = 70,
                cooldownMinutes = 60,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_night_quiet",
            title = "Gece Sessizliği",
            description = "Saat 23:00'te veya gece ekranı kapatınca Rahatsız Etmeyin'i açar, sesi tamamen kısar ve medya sesini %20'ye indirir.",
            titleRes = R.string.preset_night_quiet_title,
            descriptionRes = R.string.preset_night_quiet_desc,
            category = PresetCategory.ROUTINE,
            iconName = "DoNotDisturbOn",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Gece Sessizliği",
                triggerEvent = TriggerEvent.TIME_SCHEDULE,
                triggerDefinitions = TriggerDefinitions(
                    items = listOf(
                        TriggerDefinition.Schedule(
                            scheduledMinute = 23 * 60,
                            scheduledDays = setOf(1, 2, 3, 4, 5, 6, 7),
                        ),
                        TriggerDefinition.ScreenOff,
                    )
                ),
                scheduledMinute = 23 * 60,
                scheduledDays = setOf(1, 2, 3, 4, 5, 6, 7),
                conditions = listOf(
                    RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 22 * 60, endMinute = 8 * 60),
                ),
                conditionTree = ConditionNode.Group(
                    combinator = ConditionCombinator.AND,
                    children = listOf(
                        ConditionNode.Leaf(
                            RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 22 * 60, endMinute = 8 * 60)
                        ),
                    ),
                ),
                actions = listOf(
                    ActionType.DND_ON,
                    ActionType.SOUND_PROFILE_SILENT,
                    ActionType.SET_MEDIA_VOLUME,
                ),
                actionDelays = listOf(0, 0, 0),
                mediaVolumePercent = 20,
                cooldownMinutes = 60,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_commute_mode",
            title = "Sabah Yola Çıkış",
            description = "Sabah 06:00-10:00 arasında ev Wi-Fi'sinden ayrılınca veya şarjdan çekince mobil veriyi açar, sesi normale alır ve medya sesini %80 yapar.",
            titleRes = R.string.preset_commute_title,
            descriptionRes = R.string.preset_commute_desc,
            category = PresetCategory.CONNECTIVITY,
            iconName = "DirectionsCar",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Sabah Yola Çıkış",
                triggerEvent = TriggerEvent.WIFI_DISCONNECTED,
                triggerDefinitions = TriggerDefinitions(
                    items = listOf(
                        TriggerDefinition.WifiDisconnected(wifiSsid = "Ev Wi-Fi"),
                        TriggerDefinition.ChargerDisconnected,
                    )
                ),
                wifiSsid = "Ev Wi-Fi",
                conditions = listOf(
                    RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 6 * 60, endMinute = 10 * 60),
                ),
                conditionTree = ConditionNode.Group(
                    combinator = ConditionCombinator.AND,
                    children = listOf(
                        ConditionNode.Leaf(
                            RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 6 * 60, endMinute = 10 * 60)
                        ),
                    ),
                ),
                actions = listOf(
                    ActionType.MOBILE_DATA_ON,
                    ActionType.SOUND_PROFILE_NORMAL,
                    ActionType.SET_MEDIA_VOLUME,
                ),
                actionDelays = listOf(0, 0, 0),
                mediaVolumePercent = 80,
                cooldownMinutes = 15,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_focus_mode",
            title = "Odak / Çalışma Modu",
            description = "Hafta içi 09:00'da, 09:00-18:00 aralığındaysanız ve ev Wi-Fi'sinde değilseniz Rahatsız Etmeyin'i açar ve sesi titreşime alır.",
            titleRes = R.string.preset_focus_title,
            descriptionRes = R.string.preset_focus_desc,
            category = PresetCategory.ROUTINE,
            iconName = "Work",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Odak / Çalışma Modu",
                triggerEvent = TriggerEvent.TIME_SCHEDULE,
                scheduledMinute = 9 * 60,
                scheduledDays = setOf(1, 2, 3, 4, 5),
                conditions = listOf(
                    RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 9 * 60, endMinute = 18 * 60),
                    RuleCondition(type = ConditionType.WIFI_CONNECTED, wifiSsid = "Ev Wi-Fi"),
                ),
                conditionTree = ConditionNode.Group(
                    combinator = ConditionCombinator.AND,
                    children = listOf(
                        ConditionNode.Leaf(
                            RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 9 * 60, endMinute = 18 * 60)
                        ),
                        ConditionNode.Group(
                            combinator = ConditionCombinator.AND,
                            negate = true,
                            children = listOf(
                                ConditionNode.Leaf(
                                    RuleCondition(type = ConditionType.WIFI_CONNECTED, wifiSsid = "Ev Wi-Fi")
                                ),
                            ),
                        ),
                    ),
                ),
                actions = listOf(
                    ActionType.DND_ON,
                    ActionType.SOUND_PROFILE_VIBRATE,
                ),
                actionDelays = listOf(0, 0),
                cooldownMinutes = 30,
                createdAt = System.currentTimeMillis(),
            ),
        ),
        AutomationPreset(
            id = "preset_night_dim",
            title = "Gece Ekran Kısma",
            description = "Gece 00:00-06:00 arasında ekranı açınca, pil %60'ın altındaysa parlaklığı %10'a düşürür ve koyu temayı açar.",
            titleRes = R.string.preset_night_dim_title,
            descriptionRes = R.string.preset_night_dim_desc,
            category = PresetCategory.ROUTINE,
            iconName = "Brightness4",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Gece Ekran Kısma",
                triggerEvent = TriggerEvent.SCREEN_ON,
                conditions = listOf(
                    RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 0, endMinute = 6 * 60),
                    RuleCondition(type = ConditionType.BATTERY_BELOW, batteryLevel = 60),
                ),
                conditionTree = ConditionNode.Group(
                    combinator = ConditionCombinator.AND,
                    children = listOf(
                        ConditionNode.Leaf(
                            RuleCondition(type = ConditionType.TIME_BETWEEN, startMinute = 0, endMinute = 6 * 60)
                        ),
                        ConditionNode.Leaf(
                            RuleCondition(type = ConditionType.BATTERY_BELOW, batteryLevel = 60)
                        ),
                    ),
                ),
                actions = listOf(
                    ActionType.SET_SCREEN_BRIGHTNESS,
                    ActionType.DARK_THEME_ON,
                ),
                actionDelays = listOf(0, 0),
                screenBrightnessPercent = 10,
                createdAt = System.currentTimeMillis(),
            ),
        ),
    )
}
