package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
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
        val action = when (actionName) {
            "TORCH_ON" -> ActionType.TORCH_ON
            "TORCH_OFF" -> ActionType.TORCH_OFF
            "MUSIC_SEARCH" -> ActionType.MUSIC_SEARCH
            "MUSIC_PLAY_LINK" -> ActionType.MUSIC_PLAY_LINK
            "CREATE_CALENDAR_EVENT" -> ActionType.CREATE_CALENDAR_EVENT
            "SHOW_NOTIFICATION" -> ActionType.SHOW_NOTIFICATION
            else -> return error(ErrorReason.UNSUPPORTED_ACTION)
        }
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
            else -> return error(ErrorReason.UNSUPPORTED_ACTION)
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
                    else -> primitive()
                }
                if (fields.put(key, value) != null) throw InvalidJson()
                whitespace()
                if (take('}')) return fields
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
            while (pos < input.length && input[pos] !in ",} \t\r\n") pos++
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
