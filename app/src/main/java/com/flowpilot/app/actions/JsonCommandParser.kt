package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.NotificationDismissScope
import com.flowpilot.app.data.model.SoundPreset
import com.flowpilot.app.data.model.VibrationPattern
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** Parses one bounded command without retaining input in failure results. */
object JsonCommandParser {
    const val MAX_INPUT_LENGTH = 16_384
    private const val MAX_FIELD_LENGTH = 4_096
    private const val MAX_ID_LENGTH = 128

    enum class ErrorReason { INVALID_JSON, INPUT_TOO_LARGE, INVALID_SCHEMA, UNSUPPORTED_VERSION, UNSUPPORTED_ACTION, INVALID_PARAMETERS }

    sealed class Result {
        data class Success(val action: ActionType, val parameters: ActionParameters, val id: String?) : Result()
        data class Error(val reason: ErrorReason) : Result()
    }

    fun parse(input: String): Result {
        if (input.length > MAX_INPUT_LENGTH) return Result.Error(ErrorReason.INPUT_TOO_LARGE)
        val root = try { Reader(input.trim()).read() } catch (_: InvalidJson) {
            return Result.Error(ErrorReason.INVALID_JSON)
        }
        if (root.keys.any { it !in setOf("version", "id", "action", "params") }) return error(ErrorReason.INVALID_SCHEMA)
        val version = (root["version"] as? Value.Number)?.text
            ?: return error(ErrorReason.INVALID_SCHEMA)
        if (version != "1") return error(ErrorReason.UNSUPPORTED_VERSION)
        val id = if (root.containsKey("id")) root.string("id") ?: return error(ErrorReason.INVALID_SCHEMA) else null
        if (id != null && (id.isBlank() || id.length > MAX_ID_LENGTH)) return error(ErrorReason.INVALID_SCHEMA)
        val actionName = root.string("action") ?: return error(ErrorReason.INVALID_SCHEMA)
        val params = (root["params"] as? Value.Object)?.fields ?: return error(ErrorReason.INVALID_SCHEMA)
        if (params.values.any { it is Value.Text && it.text.length > MAX_FIELD_LENGTH }) return error(ErrorReason.INVALID_PARAMETERS)
        val action = ActionType.fromId(actionName)
            ?.takeUnless { it == ActionType.EXECUTE_JSON_COMMAND }
            ?: return error(ErrorReason.UNSUPPORTED_ACTION)
        val parameters = when (action) {
            ActionType.TORCH_ON, ActionType.TORCH_OFF -> {
                if (params.isNotEmpty()) return error(ErrorReason.INVALID_PARAMETERS)
                ActionParameters()
            }
            ActionType.MUSIC_SEARCH, ActionType.MUSIC_PLAY_LINK -> {
                val field = if (action == ActionType.MUSIC_SEARCH) "query" else "link"
                if (params.keys != setOf("appPackage", field)) return error(ErrorReason.INVALID_PARAMETERS)
                val app = params.string("appPackage") ?: return error(ErrorReason.INVALID_PARAMETERS)
                val value = params.string(field) ?: return error(ErrorReason.INVALID_PARAMETERS)
                if (!MusicLinkValidator.isSupportedPackage(app) || value.isBlank() ||
                    (action == ActionType.MUSIC_PLAY_LINK && !MusicLinkValidator.isValidLink(app, value))) {
                    return error(ErrorReason.INVALID_PARAMETERS)
                }
                ActionParameters(musicAppPackage = app, musicQuery = if (field == "query") value else "", musicLink = if (field == "link") value else "")
            }
            ActionType.SHOW_NOTIFICATION -> {
                if (params.keys != setOf("title", "body")) return error(ErrorReason.INVALID_PARAMETERS)
                val title = params.string("title") ?: return error(ErrorReason.INVALID_PARAMETERS)
                val body = params.string("body") ?: return error(ErrorReason.INVALID_PARAMETERS)
                ActionParameters(notificationTitle = title, notificationBody = body)
            }
            ActionType.CREATE_CALENDAR_EVENT -> calendar(params) ?: return error(ErrorReason.INVALID_PARAMETERS)
            else -> additionalParameters(action, params) ?: return error(ErrorReason.INVALID_PARAMETERS)
        }
        return Result.Success(action, parameters, id)
    }

    private fun calendar(params: Map<String, Value>): ActionParameters? {
        val required = setOf("title", "accountName", "accountType", "calendarName", "startMillis", "endMillis")
        val allowed = required + setOf("description", "location", "repeat", "reminderMinutes")
        if (!params.keys.containsAll(required) || params.keys.any { it !in allowed }) return null
        val title = params.string("title")?.takeUnless { it.isBlank() } ?: return null
        val accountName = params.string("accountName")?.takeUnless { it.isBlank() } ?: return null
        val accountType = params.string("accountType")?.takeUnless { it.isBlank() } ?: return null
        val calendarName = params.string("calendarName")?.takeUnless { it.isBlank() } ?: return null
        val start = params.integer("startMillis") ?: return null
        val end = params.integer("endMillis") ?: return null
        if (start <= 0 || end <= start) return null
        val description = params.optionalString("description", "") ?: return null
        val location = params.optionalString("location", "") ?: return null
        val repeat = params.optionalString("repeat", "NONE") ?: return null
        val reminder = if (params.containsKey("reminderMinutes")) params.integer("reminderMinutes") ?: return null else -1L
        if (reminder !in -1L..40_320L || !CalendarExecutor.validOptions(repeat, reminder.toInt())) return null
        return ActionParameters(calendarTitle = title, calendarDescription = description, calendarLocation = location,
            calendarAccountName = accountName, calendarAccountType = accountType, calendarName = calendarName,
            calendarStartMillis = start, calendarEndMillis = end, calendarRepeat = repeat, calendarReminderMinutes = reminder.toInt())
    }

    private fun additionalParameters(action: ActionType, p: Map<String, Value>): ActionParameters? {
        fun fields(required: Set<String>, optional: Set<String> = emptySet()) =
            p.keys.containsAll(required) && p.keys.all { it in required || it in optional }
        fun text(key: String, default: String = "") = p.optionalString(key, default)
        fun number(key: String, default: Int, range: IntRange): Int? {
            val value = if (p.containsKey(key)) p.integer(key) ?: return null else default.toLong()
            return if (value in range.first.toLong()..range.last.toLong()) value.toInt() else null
        }
        return when (action) {
            ActionType.VIBRATE -> {
                if (!fields(emptySet(), setOf("pattern", "durationMs", "amplitude"))) return null
                val pattern = text("pattern", "PULSE")?.let { name -> VibrationPattern.entries.firstOrNull { it.name == name } } ?: return null
                val duration = number("durationMs", 220, 80..800) ?: return null
                val amplitude = number("amplitude", 180, 1..255) ?: return null
                ActionParameters(vibrationPattern = pattern, vibrationDurationMs = duration, vibrationAmplitude = amplitude)
            }
            ActionType.PLAY_SOUND -> {
                if (!fields(emptySet(), setOf("preset", "uri", "durationMs"))) return null
                val preset = text("preset", "NOTIFICATION")?.let { name -> SoundPreset.entries.firstOrNull { it.name == name } } ?: return null
                val uri = text("uri") ?: return null
                if (preset == SoundPreset.CUSTOM) {
                    val parsed = try { URI(uri) } catch (_: Exception) { return null }
                    if (parsed.scheme !in setOf("content", "android.resource") || parsed.authority.isNullOrBlank()) return null
                } else if (p.containsKey("uri")) return null
                ActionParameters(soundPreset = preset, soundUri = uri, soundDurationMs = number("durationMs", 3000, 1000..60000) ?: return null)
            }
            ActionType.SET_MEDIA_VOLUME, ActionType.SET_NOTIFICATION_VOLUME, ActionType.SET_ALARM_VOLUME, ActionType.SET_SCREEN_BRIGHTNESS -> {
                if (!fields(setOf("percent"))) return null
                val percent = number("percent", 0, 0..100) ?: return null
                when (action) {
                    ActionType.SET_MEDIA_VOLUME -> ActionParameters(mediaVolumePercent = percent)
                    ActionType.SET_NOTIFICATION_VOLUME -> ActionParameters(notificationVolumePercent = percent)
                    ActionType.SET_ALARM_VOLUME -> ActionParameters(alarmVolumePercent = percent)
                    else -> ActionParameters(screenBrightnessPercent = percent)
                }
            }
            ActionType.DISMISS_NOTIFICATION -> {
                if (!fields(emptySet(), setOf("scope"))) return null
                val scope = text("scope", NotificationDismissScope.THIS.name)
                    ?.let { name -> NotificationDismissScope.entries.firstOrNull { it.name == name } } ?: return null
                ActionParameters(dismissScope = scope)
            }
            ActionType.MEDIA_PLAY, ActionType.MEDIA_PAUSE, ActionType.MEDIA_TOGGLE, ActionType.MEDIA_NEXT, ActionType.MEDIA_PREVIOUS -> {
                if (!fields(emptySet(), setOf("targetPackage"))) return null
                ActionParameters(mediaTargetPackage = text("targetPackage") ?: return null)
            }
            ActionType.SPEAK_TEXT -> {
                if (!fields(setOf("text"), setOf("voiceName", "speechRate"))) return null
                val value = p.string("text")?.takeIf { it.isNotBlank() && it.length <= 2000 } ?: return null
                val rate = if (p.containsKey("speechRate")) (p["speechRate"] as? Value.Number)?.text?.toFloatOrNull() ?: return null else 1f
                if (!rate.isFinite() || rate !in 0.5f..2f) return null
                ActionParameters(ttsText = value, ttsVoiceName = text("voiceName") ?: return null, ttsSpeechRate = rate)
            }
            ActionType.CREATE_ALARM -> {
                if (!fields(setOf("hour", "minute"), setOf("days", "message"))) return null
                val hour = number("hour", 0, 0..23) ?: return null
                val minute = number("minute", 0, 0..59) ?: return null
                val days = if (p.containsKey("days")) (p["days"] as? Value.Array)?.numbers ?: return null else emptyList()
                if (days.size > 7 || days.any { it !in 1..7 } || days.distinct().size != days.size) return null
                ActionParameters(alarmHour = hour, alarmMinute = minute, alarmDays = days, alarmMessage = text("message") ?: return null)
            }
            ActionType.START_TIMER -> {
                if (!fields(setOf("durationSeconds"), setOf("message"))) return null
                ActionParameters(timerDurationSeconds = number("durationSeconds", 0, 1..86400) ?: return null, timerMessage = text("message") ?: return null)
            }
            ActionType.LAUNCH_APP, ActionType.FORCE_STOP_APP -> {
                if (!fields(setOf("package"))) return null
                val pkg = p.string("package")?.takeUnless { it.isBlank() } ?: return null
                if (action == ActionType.LAUNCH_APP) ActionParameters(launchPackage = pkg) else ActionParameters(forceStopPackage = pkg)
            }
            ActionType.OPEN_URL -> {
                if (!fields(setOf("url"))) return null
                val url = p.string("url")?.takeUnless { it.isBlank() } ?: return null
                val uri = try { URI(url.trim()) } catch (_: Exception) { return null }
                if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
                ActionParameters(url = url)
            }
            ActionType.HTTP_WEBHOOK -> {
                if (!fields(setOf("url"), setOf("method", "headers", "body", "timeoutSeconds"))) return null
                val result = ActionParameters(webhookUrl = p.string("url") ?: return null, webhookMethod = text("method", "POST") ?: return null,
                    webhookHeaders = text("headers") ?: return null, webhookBody = text("body") ?: return null,
                    webhookTimeoutSeconds = number("timeoutSeconds", 10, 1..60) ?: return null)
                if (WebhookExecutor.validateParameters(result) != null) return null
                result
            }
            ActionType.DIAL_NUMBER, ActionType.CALL_NUMBER -> {
                if (!fields(setOf("number"))) return null
                ActionParameters(phoneNumber = p.string("number")?.takeUnless { it.isBlank() } ?: return null)
            }
            ActionType.SEND_SMS, ActionType.DRAFT_SMS -> {
                if (!fields(if (action == ActionType.SEND_SMS) setOf("recipient", "message") else emptySet(), setOf("recipient", "message"))) return null
                val recipient = text("recipient") ?: return null
                val message = text("message") ?: return null
                if (action == ActionType.SEND_SMS && (recipient.isBlank() || message.isBlank()) || recipient.isBlank() && message.isBlank()) return null
                ActionParameters(smsRecipient = recipient, smsMessage = message)
            }
            ActionType.EXECUTE_JSON_COMMAND, ActionType.TORCH_ON, ActionType.TORCH_OFF, ActionType.MUSIC_SEARCH,
            ActionType.MUSIC_PLAY_LINK, ActionType.CREATE_CALENDAR_EVENT, ActionType.SHOW_NOTIFICATION -> null
            ActionType.BLUETOOTH_ON, ActionType.BLUETOOTH_OFF, ActionType.NFC_ON, ActionType.NFC_OFF,
            ActionType.BATTERY_SAVER_ON, ActionType.BATTERY_SAVER_OFF, ActionType.DARK_THEME_ON, ActionType.DARK_THEME_OFF,
            ActionType.AUTO_ROTATE_ON, ActionType.AUTO_ROTATE_OFF, ActionType.DND_ON, ActionType.DND_OFF,
            ActionType.SOUND_PROFILE_NORMAL, ActionType.SOUND_PROFILE_VIBRATE, ActionType.SOUND_PROFILE_SILENT,
            ActionType.OPEN_DIALER, ActionType.MOBILE_DATA_ON, ActionType.MOBILE_DATA_OFF, ActionType.WIFI_ON, ActionType.WIFI_OFF,
            ActionType.AIRPLANE_MODE_ON, ActionType.AIRPLANE_MODE_OFF, ActionType.HOTSPOT_ON, ActionType.HOTSPOT_OFF,
            ActionType.TORCH_TOGGLE, ActionType.AUTO_BRIGHTNESS_ON, ActionType.AUTO_BRIGHTNESS_OFF, ActionType.LOCK_SCREEN,
            ActionType.LOCATION_ON, ActionType.LOCATION_OFF -> if (p.isEmpty()) ActionParameters() else null
        }
    }

    private fun error(reason: ErrorReason) = Result.Error(reason)
    private fun Map<String, Value>.string(key: String) = (this[key] as? Value.Text)?.text
    private fun Map<String, Value>.optionalString(key: String, default: String) = if (containsKey(key)) string(key) else default
    private fun Map<String, Value>.integer(key: String): Long? {
        val text = (this[key] as? Value.Number)?.text ?: return null
        if (!INTEGER.matches(text)) return null
        return text.toLongOrNull()
    }

    private val INTEGER = Regex("-?(0|[1-9][0-9]*)")
    private sealed class Value {
        data class Text(val text: String) : Value()
        data class Number(val text: String) : Value()
        data class Object(val fields: Map<String, Value>) : Value()
        data class Array(val numbers: List<Int>) : Value()
        object Other : Value()
    }
    private class InvalidJson : Exception()

    /** Only the root and params objects are admitted; duplicate keys never collapse silently. */
    private class Reader(private val input: String) {
        private var pos = 0
        fun read(): Map<String, Value> {
            whitespace()
            val root = objectValue(0)
            whitespace()
            if (pos != input.length) throw InvalidJson()
            return root
        }
        private fun objectValue(depth: Int): Map<String, Value> {
            if (depth > 1) throw InvalidJson()
            expect('{')
            whitespace()
            val fields = mutableMapOf<String, Value>()
            if (take('}')) return fields
            while (true) {
                val key = string()
                whitespace(); expect(':'); whitespace()
                val value = when (peek()) {
                    '"' -> Value.Text(string())
                    '{' -> Value.Object(objectValue(depth + 1))
                    '[' -> arrayValue(depth)
                    else -> primitive()
                }
                if (fields.put(key, value) != null) throw InvalidJson()
                whitespace()
                if (take('}')) return fields
                expect(','); whitespace()
            }
        }
        private fun arrayValue(depth: Int): Value.Array {
            if (depth != 1) throw InvalidJson()
            expect('['); whitespace()
            val numbers = mutableListOf<Int>()
            if (take(']')) return Value.Array(numbers)
            while (true) {
                val value = primitive() as? Value.Number ?: throw InvalidJson()
                if (!INTEGER.matches(value.text)) throw InvalidJson()
                numbers.add(value.text.toIntOrNull() ?: throw InvalidJson())
                if (numbers.size > 7) throw InvalidJson()
                whitespace()
                if (take(']')) return Value.Array(numbers)
                expect(','); whitespace()
            }
        }
        private fun string(): String {
            val start = pos
            expect('"')
            while (pos < input.length) {
                val ch = input[pos++]
                if (ch == '"') {
                    return try {
                        val primitive = Json.parseToJsonElement(input.substring(start, pos)) as JsonPrimitive
                        if (!primitive.isString) throw InvalidJson()
                        primitive.content
                    } catch (_: IllegalArgumentException) { throw InvalidJson() }
                }
                if (ch < ' ') throw InvalidJson()
                if (ch == '\\') {
                    if (pos >= input.length) throw InvalidJson()
                    val escaped = input[pos++]
                    if (escaped == 'u') {
                        repeat(4) { if (pos >= input.length || input[pos++] !in "0123456789abcdefABCDEF") throw InvalidJson() }
                    } else if (escaped !in "\"\\/bfnrt") throw InvalidJson()
                }
            }
            throw InvalidJson()
        }
        private fun primitive(): Value {
            val start = pos
            while (pos < input.length && input[pos] !in ",}] \t\r\n") pos++
            val token = input.substring(start, pos)
            if (token in setOf("true", "false", "null")) return Value.Other
            if (!NUMBER.matches(token)) throw InvalidJson()
            return Value.Number(token)
        }
        private fun whitespace() { while (pos < input.length && input[pos] in " \t\r\n") pos++ }
        private fun peek(): Char = input.getOrNull(pos) ?: throw InvalidJson()
        private fun expect(ch: Char) { if (!take(ch)) throw InvalidJson() }
        private fun take(ch: Char): Boolean = if (input.getOrNull(pos) == ch) { pos++; true } else false
    }
    private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
}
