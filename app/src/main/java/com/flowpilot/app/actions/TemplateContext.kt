package com.flowpilot.app.actions

import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Values available for `${placeholder}` substitution.
 *
 * @property trigger Event name that fired the rule, e.g. `CHARGER_CONNECTED`.
 * @property timestamp Epoch milliseconds when the event occurred.
 * @property timeProvider ISO-8601 representation of [timestamp]; overridable for deterministic tests.
 * @property batteryPercent Battery level 0-100, or null when unknown.
 * @property isCharging True when charging, false when discharging, null when unknown.
 * @property wifiSsid Connected Wi-Fi network name, or null when unknown/disconnected.
 * @property smsSender SMS sender address, or null when the event is not an SMS.
 * @property smsBody SMS message body, or null when the event is not an SMS.
 * @property smsOtp Explicit OTP override; derived from [smsBody] when null.
 * @property locationLat Latitude, or null when no location is available.
 * @property locationLng Longitude, or null when no location is available.
 * @property triggerSourceIndex 1-based index of the matched trigger inside a multi-trigger rule.
 * @property values Additional dotted variables (notification.*, call.*, app.*, rule.name, ...).
 * Media events provide `media.title` and `media.artist` here, using empty strings for missing
 * metadata. These event values are transient and must not be written to logs or history.
 */
data class TemplateContext(
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
    val triggerSourceIndex: Int? = null,
    val values: Map<String, String> = emptyMap(),
)
