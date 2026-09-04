package com.flowpilot.app.actions

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Contextual live values available for dynamic substitution in webhook headers and body.
 *
 * @property trigger The string representation of the triggering event (e.g. "CHARGER_CONNECTED", "APP_OPENED").
 * @property timestamp Epoch milliseconds when trigger occurred.
 * @property timeProvider Supplier of ISO-8601 formatted timestamp string for deterministic testing.
 * @property batteryPercent Device battery level 0-100, or null if unknown.
 * @property isCharging True if charger is connected, false if disconnected, or null if unknown.
 * @property wifiSsid Name of connected Wi-Fi network, or null if disconnected/unknown.
 */
data class WebhookTemplateContext(
    val trigger: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val timeProvider: () -> String = { DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(timestamp)) },
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val wifiSsid: String? = null,
    val smsSender: String? = null,
    val smsBody: String? = null,
    val smsOtp: String? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
)

/**
 * Pure template renderer for Webhook and SMS parameters.
 * Substitutes supported `${placeholder}` tokens with live context values.
 * Supports both dot notation (e.g. `${sms.sender}`, `${location.lat}`) and camelCase.
 * Unknown tokens are left unchanged. No recursive rendering.
 */
object WebhookTemplateRenderer {

    private val PLACEHOLDER_REGEX = Regex("""\$\{([a-zA-Z0-9_.]+)\}""")
    private val OTP_REGEX = Regex("""\b(\d{4,8})\b""")

    /**
     * Extracts an OTP or verification code (4-8 consecutive digits) from an SMS text message body.
     */
    fun extractOtp(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return OTP_REGEX.find(body)?.groupValues?.get(1)
    }

    fun render(template: String, context: WebhookTemplateContext?): String {
        if (context == null || template.isEmpty() || !template.contains("\${")) {
            return template
        }

        return PLACEHOLDER_REGEX.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            when (key) {
                "time" -> context.timeProvider()
                "timestamp" -> context.timestamp.toString()
                "batteryPercent", "battery.percent" -> context.batteryPercent?.toString() ?: ""
                "isCharging" -> context.isCharging?.toString() ?: ""
                "wifiSsid" -> context.wifiSsid ?: ""
                "trigger" -> context.trigger
                "smsSender", "sms.sender" -> context.smsSender ?: ""
                "smsBody", "sms.body" -> context.smsBody ?: ""
                "smsOtp", "sms.otp" -> context.smsOtp ?: (extractOtp(context.smsBody) ?: "")
                "locationLat", "location.lat" -> context.locationLat?.let { String.format(java.util.Locale.US, "%.6f", it) } ?: ""
                "locationLng", "location.lng" -> context.locationLng?.let { String.format(java.util.Locale.US, "%.6f", it) } ?: ""
                else -> matchResult.value // Unknown/unsupported token left unchanged
            }
        }
    }
}
