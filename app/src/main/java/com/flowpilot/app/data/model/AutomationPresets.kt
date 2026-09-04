package com.flowpilot.app.data.model

import java.util.UUID

/**
 * Predefined automation preset categories for UI filtering.
 */
enum class PresetCategory(val label: String) {
    ROUTINE("Rutinler"),
    BATTERY("Pil & Güç"),
    GESTURES("Sensör & Jestler"),
    SAFETY_LOCATION("Güvenlik & Konum"),
    CONNECTIVITY("Ağ & Bağlantı"),
}

/**
 * A ready-to-use automation template that can be applied to the creation form with a single tap.
 */
data class AutomationPreset(
    val id: String,
    val title: String,
    val description: String,
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
            category = PresetCategory.BATTERY,
            iconName = "BatteryAlert",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Acil Pil Tasarrufu",
                triggerEvent = TriggerEvent.BATTERY_BELOW,
                batteryLevel = 15,
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
            title = "Sallayarak Feneri Aç",
            description = "Telefon sallandığında arka kamera fenerini açar ve çift titreşimle geri bildirim verir.",
            category = PresetCategory.GESTURES,
            iconName = "FlashlightOn",
            template = Automation(
                id = UUID.randomUUID().toString(),
                name = "Sallayarak Feneri Aç",
                triggerEvent = TriggerEvent.DEVICE_SHAKE,
                actions = listOf(
                    ActionType.TORCH_ON,
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
    )
}
