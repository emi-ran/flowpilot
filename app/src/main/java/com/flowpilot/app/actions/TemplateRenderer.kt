package com.flowpilot.app.actions

import java.net.URLEncoder
import java.util.Locale

/** Rendering behavior for unknown variables and malformed expressions. */
enum class RenderMode { LENIENT, STRICT }

/**
 * Outcome of [TemplateRenderer.renderResult].
 *
 * @property text Rendered text. Unknown variables and malformed expressions keep their original token.
 * @property unknownVariables Names of variables that were not resolvable (populated in [RenderMode.STRICT]).
 * @property parseErrors Human-readable descriptions of malformed or oversized expressions.
 * @property expressionCount Number of syntactically valid, unescaped expressions.
 */
data class RenderResult(
    val text: String,
    val unknownVariables: Set<String> = emptySet(),
    val parseErrors: List<String> = emptyList(),
    val expressionCount: Int = 0,
)

/**
 * Pure, single-pass `${placeholder}` renderer with optional filters.
 *
 * Syntax:
 * ```
 * ${name}
 * ${name|filter}
 * ${name|filter:"argument"|filter2}
 * ```
 *
 * Rules:
 * - `$${` produces a literal `${`.
 * - Replacement values are never re-scanned, so rendering is not recursive.
 * - Quoted filter arguments may contain spaces, braces, pipes and `\"` / `\\` escapes.
 * - [RenderMode.LENIENT] leaves unknown variables untouched; [RenderMode.STRICT] additionally reports them.
 * - Malformed expressions are never partially rendered: the original token is kept and a parse error is reported.
 * - A null [TemplateContext] disables substitution and returns the template unchanged.
 */
object TemplateRenderer {

    const val MAX_TEMPLATE_LENGTH = 4000
    const val MAX_FILTERS_PER_EXPRESSION = 8
    const val MAX_VARIABLE_NAME_LENGTH = 64
    private const val MAX_PREVIEW_LENGTH = 80

    private val VARIABLE_NAME_REGEX = Regex("[A-Za-z0-9_.]+")
    private val FILTER_NAME_REGEX = Regex("[A-Za-z][A-Za-z0-9_]*")
    private val OTP_REGEX = Regex("""\b(\d{4,8})\b""")

    private enum class FilterArity { NONE, REQUIRED, REQUIRED_NUMERIC }

    private val FILTER_ARITY: Map<String, FilterArity> = mapOf(
        "trim" to FilterArity.NONE,
        "lower" to FilterArity.NONE,
        "upper" to FilterArity.NONE,
        "urlencode" to FilterArity.NONE,
        "jsonescape" to FilterArity.NONE,
        "stripprefix" to FilterArity.REQUIRED,
        "default" to FilterArity.REQUIRED,
        "drop" to FilterArity.REQUIRED_NUMERIC,
        "truncate" to FilterArity.REQUIRED_NUMERIC,
    )

    private data class Filter(val name: String, val argument: String?)

    private sealed interface ExpressionOutcome {
        data class Success(val name: String, val filters: List<Filter>) : ExpressionOutcome
        data class Failure(val message: String) : ExpressionOutcome
    }

    private sealed interface FilterOutcome {
        data class Success(val filter: Filter) : FilterOutcome
        data class Failure(val message: String) : FilterOutcome
    }

    /** Extracts an OTP or verification code (4-8 consecutive digits) from an SMS body. */
    fun extractOtp(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return OTP_REGEX.find(body)?.groupValues?.get(1)
    }

    fun render(
        template: String,
        context: TemplateContext?,
        mode: RenderMode = RenderMode.LENIENT,
    ): String = renderResult(template, context, mode).text

    fun renderResult(
        template: String,
        context: TemplateContext?,
        mode: RenderMode = RenderMode.LENIENT,
    ): RenderResult {
        if (context == null || template.isEmpty()) {
            return RenderResult(template)
        }
        if (template.length > MAX_TEMPLATE_LENGTH) {
            return RenderResult(
                text = template,
                parseErrors = listOf(
                    "template length ${template.length} exceeds $MAX_TEMPLATE_LENGTH characters",
                ),
            )
        }
        if (!template.contains("\${")) {
            return RenderResult(template)
        }

        val out = StringBuilder(template.length)
        val unknownVariables = LinkedHashSet<String>()
        val parseErrors = ArrayList<String>()
        var expressionCount = 0
        var index = 0
        while (index < template.length) {
            if (isEscapedOpening(template, index)) {
                out.append("\${")
                index += 3
                continue
            }
            if (template[index] == '$' && index + 1 < template.length && template[index + 1] == '{') {
                val closeIndex = findExpressionEnd(template, index + 2)
                if (closeIndex < 0) {
                    parseErrors += "unterminated expression at index $index"
                    out.append(template, index, template.length)
                    break
                }
                val raw = template.substring(index, closeIndex + 1)
                val inner = template.substring(index + 2, closeIndex)
                if (hasUnquotedOpening(inner)) {
                    // A missing closing brace swallowed a later expression, e.g. `${malformed ${trigger}`.
                    // Treat this `$` as literal so the later, well-formed expression still renders.
                    parseErrors += "malformed expression at index $index: nested expression"
                    out.append('$')
                    index++
                    continue
                }
                when (val parsed = parseExpression(inner)) {
                    is ExpressionOutcome.Success -> {
                        expressionCount++
                        val value = resolve(parsed.name, context)
                        if (value == null) {
                            if (mode == RenderMode.STRICT) unknownVariables += parsed.name
                            out.append(raw)
                        } else {
                            out.append(applyFilters(value, parsed.filters))
                        }
                    }
                    is ExpressionOutcome.Failure -> {
                        parseErrors += parsed.message
                        out.append(raw)
                    }
                }
                index = closeIndex + 1
                continue
            }
            out.append(template[index])
            index++
        }

        return RenderResult(
            text = out.toString(),
            unknownVariables = unknownVariables,
            expressionCount = expressionCount,
            parseErrors = parseErrors,
        )
    }

    private fun isEscapedOpening(template: String, index: Int): Boolean =
        template[index] == '$' &&
            index + 2 < template.length &&
            template[index + 1] == '$' &&
            template[index + 2] == '{'

    /** True when the text starts a `${` outside quoted filter arguments. */
    private fun hasUnquotedOpening(text: String): Boolean {
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (inQuotes) {
                when {
                    char == '\\' && index + 1 < text.length -> index += 2
                    char == '"' -> {
                        inQuotes = false
                        index++
                    }
                    else -> index++
                }
                continue
            }
            when {
                char == '"' -> {
                    inQuotes = true
                    index++
                }
                char == '$' && index + 1 < text.length && text[index + 1] == '{' -> return true
                else -> index++
            }
        }
        return false
    }

    /** Returns the index of the terminating `}` outside quotes, or -1 when the expression is unterminated. */
    private fun findExpressionEnd(template: String, start: Int): Int {
        var index = start
        var inQuotes = false
        while (index < template.length) {
            val char = template[index]
            if (inQuotes) {
                when {
                    char == '\\' && index + 1 < template.length -> index += 2
                    char == '"' -> {
                        inQuotes = false
                        index++
                    }
                    else -> index++
                }
            } else {
                when (char) {
                    '"' -> {
                        inQuotes = true
                        index++
                    }
                    '}' -> return index
                    else -> index++
                }
            }
        }
        return -1
    }

    private fun parseExpression(inner: String): ExpressionOutcome {
        val segments = splitOutsideQuotes(inner)
            ?: return ExpressionOutcome.Failure("unbalanced quotes in expression")
        val name = segments.firstOrNull()?.trim().orEmpty()
        if (name.isEmpty()) {
            return ExpressionOutcome.Failure("empty variable name in expression")
        }
        if (name.length > MAX_VARIABLE_NAME_LENGTH) {
            return ExpressionOutcome.Failure(
                "variable name exceeds $MAX_VARIABLE_NAME_LENGTH characters: ${preview(name)}",
            )
        }
        if (!VARIABLE_NAME_REGEX.matches(name)) {
            return ExpressionOutcome.Failure("invalid variable name: ${preview(name)}")
        }
        val filterSegments = segments.drop(1)
        if (filterSegments.size > MAX_FILTERS_PER_EXPRESSION) {
            return ExpressionOutcome.Failure(
                "too many filters (${filterSegments.size}) in expression, maximum is $MAX_FILTERS_PER_EXPRESSION",
            )
        }
        val filters = ArrayList<Filter>(filterSegments.size)
        for (segment in filterSegments) {
            when (val outcome = parseFilter(segment)) {
                is FilterOutcome.Success -> filters += outcome.filter
                is FilterOutcome.Failure -> return ExpressionOutcome.Failure(outcome.message)
            }
        }
        return ExpressionOutcome.Success(name, filters)
    }

    private fun parseFilter(segment: String): FilterOutcome {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) {
            return FilterOutcome.Failure("empty filter in expression")
        }
        val colonIndex = indexOfOutsideQuotes(trimmed, ':')
        val rawName = if (colonIndex < 0) trimmed else trimmed.substring(0, colonIndex).trim()
        val rawArgument = if (colonIndex < 0) null else trimmed.substring(colonIndex + 1).trim()
        if (!FILTER_NAME_REGEX.matches(rawName)) {
            return FilterOutcome.Failure("invalid filter name: ${preview(rawName)}")
        }
        val name = rawName.lowercase(Locale.ROOT)
        val arity = FILTER_ARITY[name]
            ?: return FilterOutcome.Failure("unknown filter: ${preview(rawName)}")

        val argument = when {
            rawArgument == null -> {
                if (arity != FilterArity.NONE) {
                    return FilterOutcome.Failure("filter '$name' requires an argument")
                }
                null
            }
            arity == FilterArity.NONE -> {
                return FilterOutcome.Failure("filter '$name' does not take an argument")
            }
            else -> unquoteArgument(rawArgument)
                ?: return FilterOutcome.Failure("malformed argument for filter '$name'")
        }

        if (arity == FilterArity.REQUIRED_NUMERIC) {
            val number = argument?.toIntOrNull()
            if (number == null || number < 0) {
                return FilterOutcome.Failure("filter '$name' expects a non-negative integer argument")
            }
        }

        return FilterOutcome.Success(Filter(name, argument))
    }

    /** Splits on `|` outside quotes; returns null when quotes are unbalanced. */
    private fun splitOutsideQuotes(text: String): List<String>? {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (inQuotes) {
                if (char == '\\' && index + 1 < text.length) {
                    current.append(char).append(text[index + 1])
                    index += 2
                    continue
                }
                if (char == '"') inQuotes = false
                current.append(char)
            } else {
                when (char) {
                    '"' -> {
                        inQuotes = true
                        current.append(char)
                    }
                    '|' -> {
                        parts += current.toString()
                        current.clear()
                    }
                    else -> current.append(char)
                }
            }
            index++
        }
        if (inQuotes) return null
        parts += current.toString()
        return parts
    }

    private fun indexOfOutsideQuotes(text: String, target: Char): Int {
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (inQuotes) {
                when {
                    char == '\\' && index + 1 < text.length -> index += 2
                    char == '"' -> inQuotes = false
                }
            } else {
                if (char == '"') {
                    inQuotes = true
                } else if (char == target) {
                    return index
                }
            }
            index++
        }
        return -1
    }

    /** Returns the unescaped argument, or null when a quoted argument is malformed. */
    private fun unquoteArgument(raw: String): String? {
        if (raw.isEmpty() || raw[0] != '"') return raw
        if (raw.length < 2 || raw[raw.length - 1] != '"') return null
        val body = raw.substring(1, raw.length - 1)
        val out = StringBuilder(body.length)
        var index = 0
        while (index < body.length) {
            val char = body[index]
            if (char == '\\' && index + 1 < body.length) {
                val escaped = body[index + 1]
                if (escaped == '"' || escaped == '\\') {
                    out.append(escaped)
                } else {
                    out.append(char).append(escaped)
                }
                index += 2
                continue
            }
            if (char == '"') return null
            out.append(char)
            index++
        }
        return out.toString()
    }

    private fun applyFilters(value: String, filters: List<Filter>): String {
        var result = value
        for (filter in filters) {
            result = when (filter.name) {
                "trim" -> result.trim()
                "stripprefix" -> stripPrefix(result, filter.argument.orEmpty())
                "drop" -> dropCodePoints(result, filter.argument?.toIntOrNull() ?: 0)
                "truncate" -> truncateCodePoints(result, filter.argument?.toIntOrNull() ?: 0)
                "lower" -> result.lowercase(Locale.ROOT)
                "upper" -> result.uppercase(Locale.ROOT)
                "default" -> if (result.isEmpty()) filter.argument.orEmpty() else result
                "urlencode" -> URLEncoder.encode(result, "UTF-8").replace("+", "%20")
                "jsonescape" -> jsonEscape(result)
                else -> result
            }
        }
        return result
    }

    private fun stripPrefix(value: String, prefix: String): String {
        if (prefix.isEmpty() || !value.startsWith(prefix, ignoreCase = true)) return value
        return value.substring(prefix.length).trimStart()
    }

    private fun dropCodePoints(value: String, count: Int): String {
        if (count <= 0) return value
        val codePointCount = value.codePointCount(0, value.length)
        if (count >= codePointCount) return ""
        return value.substring(value.offsetByCodePoints(0, count))
    }

    private fun truncateCodePoints(value: String, count: Int): String {
        if (count <= 0) return ""
        val codePointCount = value.codePointCount(0, value.length)
        if (count >= codePointCount) return value
        return value.substring(0, value.offsetByCodePoints(0, count))
    }

    private fun jsonEscape(value: String): String {
        val out = StringBuilder(value.length + 8)
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (char < ' ') {
                        out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(char)
                    }
                }
            }
        }
        return out.toString()
    }

    /** Resolves [name], or returns null when the variable is unknown. Known null values resolve to "". */
    private fun resolve(name: String, context: TemplateContext): String? {
        context.values[name]?.let { return it }
        return when (name) {
            "trigger" -> context.trigger
            "trigger.index" -> context.triggerSourceIndex?.toString() ?: ""
            "time" -> context.timeProvider()
            "timestamp" -> context.timestamp.toString()
            "batteryPercent", "battery.percent" -> context.batteryPercent?.toString() ?: ""
            "isCharging", "charger.connected" -> context.isCharging?.toString() ?: ""
            "wifiSsid", "wifi.ssid" -> context.wifiSsid ?: ""
            "smsSender", "sms.sender" -> context.smsSender ?: ""
            "smsBody", "sms.body" -> context.smsBody ?: ""
            "smsOtp", "sms.otp" -> context.smsOtp ?: extractOtp(context.smsBody) ?: ""
            "locationLat", "location.lat" -> formatCoordinate(context.locationLat)
            "locationLng", "location.lng" -> formatCoordinate(context.locationLng)
            "location.maps_url", "maps_url" -> mapsUrl(context.locationLat, context.locationLng)
            "location.coords", "coords" -> formatCoordinates(context.locationLat, context.locationLng)
            else -> null
        }
    }

    private fun formatCoordinate(value: Double?): String =
        value?.let { String.format(Locale.US, "%.6f", it) } ?: ""

    private fun formatCoordinates(lat: Double?, lng: Double?): String =
        if (lat != null && lng != null) String.format(Locale.US, "%.6f,%.6f", lat, lng) else ""

    private fun mapsUrl(lat: Double?, lng: Double?): String {
        val coordinates = formatCoordinates(lat, lng)
        return if (coordinates.isEmpty()) "" else "https://maps.google.com/?q=$coordinates"
    }

    private fun preview(value: String): String =
        if (value.length <= MAX_PREVIEW_LENGTH) value else value.take(MAX_PREVIEW_LENGTH) + "..."
}
