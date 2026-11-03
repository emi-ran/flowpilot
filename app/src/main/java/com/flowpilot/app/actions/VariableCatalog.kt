package com.flowpilot.app.actions

import com.flowpilot.app.R
import com.flowpilot.app.data.model.TriggerEvent

/** One `${placeholder}` offered by the editor, together with a localizable explanation. */
data class VariableSpec(
    val name: String,
    val descriptionRes: Int,
    val sampleValue: String,
    val filterExample: String,
) {
    /** Insertion-safe token; deliberately contains no other markup. */
    val token: String get() = "\${$name}"
}

/**
 * A picker section. Groups with an empty [triggers] set are always available; other groups are
 * shown only when the rule actually reacts to one of their triggers.
 */
data class VariableGroup(
    val id: String,
    val titleRes: Int,
    val triggers: Set<TriggerEvent>,
    val specs: List<VariableSpec>,
) {
    val isAlwaysAvailable: Boolean get() = triggers.isEmpty()
}

/**
 * Trigger-aware catalog of template variables. It intentionally excludes clipboard, contacts,
 * notification history, and any other private data outside the supported trigger payloads.
 *
 * This catalog is deliberately UI-free so import validation can use the same strict rendering
 * rules as the editor. String resources are referenced by ID only.
 */
object VariableCatalog {

    const val SAMPLE_TIME: String = "2026-11-03T09:41:00Z"
    const val SAMPLE_TIMESTAMP: Long = 1_793_708_460_000L

    private fun spec(
        name: String,
        descriptionRes: Int,
        sampleValue: String,
        filterExample: String,
    ) = VariableSpec(name, descriptionRes, sampleValue, filterExample)

    val groups: List<VariableGroup> = listOf(
        VariableGroup(
            id = "global",
            titleRes = R.string.variable_group_global,
            triggers = emptySet(),
            specs = listOf(
                spec("trigger", R.string.variable_desc_trigger, "CHARGER_CONNECTED", "\${trigger|lower}"),
                spec("trigger.index", R.string.variable_desc_trigger_index, "1", "\${trigger.index|default:\"1\"}"),
                spec("time", R.string.variable_desc_time, SAMPLE_TIME, "\${time|truncate:19}"),
                spec("timestamp", R.string.variable_desc_timestamp, SAMPLE_TIMESTAMP.toString(), "\${timestamp|truncate:13}"),
                spec("batteryPercent", R.string.variable_desc_battery_percent, "82", "\${batteryPercent|default:\"0\"}"),
                spec("battery.percent", R.string.variable_desc_battery_percent, "82", "\${battery.percent|default:\"0\"}"),
                spec("isCharging", R.string.variable_desc_is_charging, "true", "\${isCharging|default:\"false\"}"),
                spec("wifiSsid", R.string.variable_desc_wifi_ssid, "Home_5G", "\${wifiSsid|default:\"Home_5G\"}"),
                spec("rule.name", R.string.variable_desc_rule_name, "Morning routine", "\${rule.name|truncate:40}"),
            ),
        ),
        VariableGroup(
            id = "notification",
            titleRes = R.string.variable_group_notification,
            triggers = setOf(TriggerEvent.NOTIFICATION_RECEIVED),
            specs = listOf(
                spec("notification.title", R.string.variable_desc_notification_title, "Alice", "\${notification.title|default:\"No title\"}"),
                spec("notification.text", R.string.variable_desc_notification_text, "TTS bugün nasılsın", "\${notification.text|stripPrefix:\"tts\"|trim}"),
                spec("notification.bigText", R.string.variable_desc_notification_big_text, "TTS bugün nasılsın", "\${notification.bigText|truncate:120}"),
                spec("notification.package", R.string.variable_desc_notification_package, "com.whatsapp", "\${notification.package|lower}"),
                spec("notification.app", R.string.variable_desc_notification_app, "WhatsApp", "\${notification.app|lower}"),
            ),
        ),
        VariableGroup(
            id = "sms",
            titleRes = R.string.variable_group_sms,
            triggers = setOf(TriggerEvent.SMS_RECEIVED),
            specs = listOf(
                spec("sms.sender", R.string.variable_desc_sms_sender, "+90 555 000 00 00", "\${sms.sender|default:\"Unknown\"}"),
                spec("smsSender", R.string.variable_desc_sms_sender, "+90 555 000 00 00", "\${smsSender|default:\"Unknown\"}"),
                spec("sms.body", R.string.variable_desc_sms_body, "Kod: 123456", "\${sms.body|truncate:120}"),
                spec("smsBody", R.string.variable_desc_sms_body, "Kod: 123456", "\${smsBody|truncate:120}"),
                spec("sms.otp", R.string.variable_desc_sms_otp, "123456", "\${sms.otp|default:\"------\"}"),
                spec("smsOtp", R.string.variable_desc_sms_otp, "123456", "\${smsOtp|default:\"------\"}"),
            ),
        ),
        VariableGroup(
            id = "call",
            titleRes = R.string.variable_group_call,
            triggers = setOf(
                TriggerEvent.CALL_RINGING,
                TriggerEvent.CALL_ANSWERED,
                TriggerEvent.CALL_OUTGOING,
                TriggerEvent.CALL_ENDED,
            ),
            specs = listOf(
                spec("call.number", R.string.variable_desc_call_number, "+90 555 111 22 33", "\${call.number|default:\"Unknown\"}"),
                spec("call.state", R.string.variable_desc_call_state, "RINGING", "\${call.state|lower}"),
            ),
        ),
        VariableGroup(
            id = "app",
            titleRes = R.string.variable_group_app,
            triggers = setOf(TriggerEvent.APP_OPENED, TriggerEvent.APP_CLOSED),
            specs = listOf(
                spec("app.package", R.string.variable_desc_app_package, "com.example.app", "\${app.package|lower}"),
                spec("app.name", R.string.variable_desc_app_name, "Example App", "\${app.name|truncate:40}"),
            ),
        ),
        VariableGroup(
            id = "wifi",
            titleRes = R.string.variable_group_wifi,
            triggers = setOf(TriggerEvent.WIFI_CONNECTED, TriggerEvent.WIFI_DISCONNECTED),
            specs = listOf(
                spec("wifi.ssid", R.string.variable_desc_wifi_group_ssid, "Home_5G", "\${wifi.ssid|default:\"Home_5G\"}"),
            ),
        ),
        VariableGroup(
            id = "bluetooth",
            titleRes = R.string.variable_group_bluetooth,
            triggers = setOf(TriggerEvent.BLUETOOTH_CONNECTED, TriggerEvent.BLUETOOTH_DISCONNECTED),
            specs = listOf(
                spec("bluetooth.name", R.string.variable_desc_bluetooth_name, "Pixel Buds", "\${bluetooth.name|default:\"Device\"}"),
                spec("bluetooth.address", R.string.variable_desc_bluetooth_address, "AA:BB:CC:DD:EE:FF", "\${bluetooth.address|upper}"),
            ),
        ),
        VariableGroup(
            id = "nfc",
            titleRes = R.string.variable_group_nfc,
            triggers = setOf(TriggerEvent.NFC_TAG_SCANNED),
            specs = listOf(
                spec("nfc.tag", R.string.variable_desc_nfc_tag, "04A1B2C3D4E5F6", "\${nfc.tag|lower}"),
            ),
        ),
        VariableGroup(
            id = "motion",
            titleRes = R.string.variable_group_motion,
            triggers = setOf(TriggerEvent.DEVICE_SHAKE),
            specs = listOf(
                spec("shake.magnitude", R.string.variable_desc_shake_magnitude, "12.4", "\${shake.magnitude|truncate:4}"),
            ),
        ),
        VariableGroup(
            id = "light",
            titleRes = R.string.variable_group_light,
            triggers = setOf(TriggerEvent.LIGHT_BELOW, TriggerEvent.LIGHT_ABOVE),
            specs = listOf(
                spec("light.lux", R.string.variable_desc_light_lux, "42", "\${light.lux|default:\"0\"}"),
            ),
        ),
        VariableGroup(
            id = "location",
            titleRes = R.string.variable_group_location,
            triggers = setOf(TriggerEvent.GEOFENCE_ENTER, TriggerEvent.GEOFENCE_EXIT),
            specs = listOf(
                spec("geofence.name", R.string.variable_desc_geofence_name, "Home", "\${geofence.name|default:\"Home\"}"),
                spec("location.lat", R.string.variable_desc_location_lat, "41.008240", "\${location.lat|truncate:8}"),
                spec("location.lng", R.string.variable_desc_location_lng, "28.978359", "\${location.lng|truncate:8}"),
                spec("location.maps_url", R.string.variable_desc_location_maps_url, "https://maps.google.com/?q=41.008240,28.978359", "\${location.maps_url|urlencode}"),
            ),
        ),
        VariableGroup(
            id = "screen",
            titleRes = R.string.variable_group_screen,
            triggers = setOf(TriggerEvent.SCREEN_ON, TriggerEvent.SCREEN_OFF, TriggerEvent.DEVICE_UNLOCKED),
            specs = listOf(
                spec("screen.on", R.string.variable_desc_screen_on, "true", "\${screen.on|default:\"false\"}"),
            ),
        ),
        VariableGroup(
            id = "power",
            titleRes = R.string.variable_group_power,
            triggers = setOf(
                TriggerEvent.CHARGER_CONNECTED,
                TriggerEvent.CHARGER_DISCONNECTED,
                TriggerEvent.BATTERY_BELOW,
                TriggerEvent.BATTERY_ABOVE,
            ),
            specs = listOf(
                spec("charger.connected", R.string.variable_desc_charger_connected, "true", "\${charger.connected|default:\"false\"}"),
            ),
        ),
    )

    private val specsByName: Map<String, VariableSpec> = groups.flatMap { it.specs }.associateBy { it.name }

    /** All tokens the editor can insert, each mapped to its catalog entry. */
    val allSpecs: List<VariableSpec> get() = specsByName.values.toList()

    /** Always includes global variables; event groups are added only for matching triggers. */
    fun availableFor(triggers: Collection<TriggerEvent>): List<VariableGroup> {
        val events = triggers.toSet()
        return groups.filter { group ->
            group.isAlwaysAvailable || group.triggers.any { it in events }
        }
    }

    fun specsFor(triggers: Collection<TriggerEvent>): List<VariableSpec> =
        availableFor(triggers).flatMap { it.specs }

    /**
     * Sample values for every available token. Used for preview rendering and strict import
     * validation; these values are never persisted. [notificationText] replaces the default
     * notification text and expanded text so the editor can speak a sample in the selected voice's
     * language.
     */
    fun sampleValues(
        triggers: Collection<TriggerEvent>,
        notificationText: String? = null,
    ): Map<String, String> =
        specsFor(triggers).associate { spec ->
            val sampleValue = if (
                notificationText != null &&
                (spec.name == "notification.text" || spec.name == "notification.bigText")
            ) {
                notificationText
            } else {
                spec.sampleValue
            }
            spec.name to sampleValue
        }

    /** Transient preview context; no sample value is written to a rule or history entry. */
    fun sampleContext(
        triggers: Collection<TriggerEvent>,
        notificationText: String? = null,
    ): TemplateContext {
        val events = triggers.toList()
        val sampleTrigger = events.firstOrNull()?.name ?: "MANUAL"
        val values = sampleValues(events, notificationText) + ("trigger" to sampleTrigger)
        return TemplateContext(
            trigger = sampleTrigger,
            timestamp = SAMPLE_TIMESTAMP,
            timeProvider = { SAMPLE_TIME },
            triggerSourceIndex = 1,
            values = values,
        )
    }

    const val MAX_RENDERED_TTS_CHARS = 2_000

    /** Strict render used by preview, save gating, and import validation. */
    fun renderSample(
        template: String,
        triggers: Collection<TriggerEvent>,
        notificationText: String? = null,
    ): RenderResult =
        TemplateRenderer.renderResult(
            template,
            sampleContext(triggers, notificationText),
            RenderMode.STRICT,
        )

    fun isTemplateValid(template: String, triggers: Collection<TriggerEvent>): Boolean {
        val result = renderSample(template, triggers)
        return result.unknownVariables.isEmpty() &&
            result.parseErrors.isEmpty() &&
            result.text.length <= MAX_RENDERED_TTS_CHARS
    }
}
