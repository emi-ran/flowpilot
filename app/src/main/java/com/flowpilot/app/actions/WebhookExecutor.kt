package com.flowpilot.app.actions

import android.util.Log
import com.flowpilot.app.data.model.ActionType
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL

internal class PinnedSocketFactory(
    private val delegate: SSLSocketFactory,
    private val address: InetAddress,
    private val hostname: String,
) : SSLSocketFactory() {
    private fun connect(port: Int, localAddress: InetAddress? = null, localPort: Int = 0): Socket =
        delegate.createSocket().apply {
            if (localAddress != null) bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }

    private fun layer(socket: Socket, port: Int, autoClose: Boolean = true): Socket =
        delegate.createSocket(socket, hostname, port, autoClose)

    override fun createSocket(host: String, port: Int): Socket = layer(connect(port), port)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        layer(connect(port, localHost, localPort), port)

    override fun createSocket(address: InetAddress, port: Int): Socket = layer(connect(port), port)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        layer(connect(port, localAddress, localPort), port)

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        if (socket.isConnected && socket.inetAddress != address) {
            throw SecurityException("Socket is not connected to validated address")
        }
        if (!socket.isConnected) socket.connect(InetSocketAddress(address, port))
        return layer(socket, port, autoClose)
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
}

/**
 * Executes outbound HTTP/HTTPS requests (webhooks) using standard HttpURLConnection.
 * Validates URLs, bounds timeouts, handles headers/bodies, redacts secrets in logs and failure messages.
 */
class WebhookExecutor(
    private val connectionFactory: (URL, InetAddress) -> HttpURLConnection = { url, address ->
        (url.openConnection() as HttpURLConnection).apply {
            if (this is HttpsURLConnection) {
                sslSocketFactory = PinnedSocketFactory(sslSocketFactory, address, url.host)
            }
        }
    },
    private val addressLookup: (String) -> Array<InetAddress> = InetAddress::getAllByName,
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(ActionType.HTTP_WEBHOOK)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.HTTP_WEBHOOK) {
            return ActionResult(false, "Unsupported action for Webhook: ${action.name}")
        }

        val validationError = validateParameters(parameters)
        if (validationError != null) {
            return ActionResult(false, validationError)
        }

        val rawUrl = parameters.webhookUrl.trim()
        val method = parameters.webhookMethod.trim().uppercase()
        val timeoutMs = parameters.webhookTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS) * 1000
        val renderedHeaders = WebhookTemplateRenderer.render(parameters.webhookHeaders, parameters.webhookTemplateContext)
        val headers = parseHeaders(renderedHeaders)
        val body = WebhookTemplateRenderer.render(parameters.webhookBody, parameters.webhookTemplateContext)

        val sanitizedHeaders = sanitizeHeadersForLogging(headers)
        val sanitizedUrl = sanitizeUrlForLogging(rawUrl)
        Log.i(TAG, "Dispatching HTTP Webhook: method=$method, url=$sanitizedUrl, timeout=${timeoutMs}ms, headers=$sanitizedHeaders")

        var connection: HttpURLConnection? = null
        return try {
            val url = URI(rawUrl).toURL()
            val address = validateResolvedAddresses(url.host)
            connection = connectionFactory(url, address).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                useCaches = false
                doInput = true

                headers.forEach { (name, value) ->
                    setRequestProperty(name, value)
                }

                if (method in METHODS_WITH_BODY && body.isNotEmpty()) {
                    doOutput = true
                    val bytes = body.toByteArray(StandardCharsets.UTF_8)
                    setFixedLengthStreamingMode(bytes.size)
                    outputStream.use { os ->
                        os.write(bytes)
                        os.flush()
                    }
                } else if (method in METHODS_WITH_BODY && body.isEmpty()) {
                    // For POST/PUT/PATCH with empty body, ensure Content-Length is 0 if no output stream written
                    setFixedLengthStreamingMode(0)
                }
            }

            val statusCode = connection.responseCode
            val isSuccess = statusCode in 200..299
            val message = if (isSuccess) {
                "HTTP Webhook delivered: status $statusCode"
            } else {
                "HTTP Webhook failed: status $statusCode"
            }
            Log.i(TAG, "HTTP Webhook result: success=$isSuccess, status=$statusCode")
            ActionResult(isSuccess, message)
        } catch (e: Exception) {
            val safeMessage = redactSensitiveText(e.message ?: e.javaClass.simpleName)
            Log.w(TAG, "HTTP Webhook execution failed: $safeMessage")
            ActionResult(false, "HTTP request failed: $safeMessage")
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {}
        }
    }

    private fun validateResolvedAddresses(host: String): InetAddress {
        val addresses = addressLookup(host)
        if (addresses.isEmpty() || addresses.any { !isPublicAddress(it) }) {
            throw SecurityException("Webhook URL resolves to a non-public address")
        }
        return addresses.first()
    }

    private fun URL.withAddress(address: InetAddress): URL {
        val hostAddress = address.hostAddress.let { if (address is Inet6Address) "[$it]" else it }
        return URL(protocol, hostAddress, port, file)
    }

    companion object {
        private fun isPublicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isMulticastAddress || address.isSiteLocalAddress) {
                return false
            }
            val bytes = address.address
            if (bytes.size == 4) {
                val first = bytes[0].toInt() and 0xff
                val second = bytes[1].toInt() and 0xff
                val third = bytes[2].toInt() and 0xff
                val fourth = bytes[3].toInt() and 0xff
                if (first == 0 || first >= 224) return false
                if (first == 100 && second in 64..127) return false
                if (first == 192 && second == 0 && third == 0) return false
                if (first == 192 && second == 0 && third == 2) return false
                if (first == 192 && second == 88 && third == 99) return false
                if (first == 198 && second in 18..19) return false
                if (first == 198 && second == 51 && third == 100) return false
                if (first == 203 && second == 0 && third == 113) return false
            } else if (address is Inet6Address) {
                if (bytes.size >= 12 && bytes.copyOfRange(0, 10).all { it == 0.toByte() } &&
                    bytes[10].toInt() and 0xff == 0xff && bytes[11].toInt() and 0xff == 0xff
                ) {
                    val mappedIpv4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                    return isPublicAddress(mappedIpv4)
                }
                val first = bytes[0].toInt() and 0xff
                val firstWord = (first shl 8) or (bytes[1].toInt() and 0xff)
                val secondWord = ((bytes[2].toInt() and 0xff) shl 8) or (bytes[3].toInt() and 0xff)
                if (first and 0xfe == 0xfc || firstWord == 0x2001 && secondWord == 0x0000 ||
                    firstWord == 0x2001 && secondWord in 0x0010..0x002f || firstWord == 0x2001 && secondWord == 0x0db8 ||
                    firstWord == 0x2002 || firstWord == 0x0064 && secondWord == 0xff9b || firstWord == 0x0100
                ) return false
                if (address.isIPv4CompatibleAddress) return false
            }
            return true
        }

        const val TAG = "FlowPilotWebhook"
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 60
        val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
        val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

        private val SENSITIVE_HEADER_KEYS = setOf(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key",
            "apikey",
            "token",
            "secret",
            "auth-token",
            "x-auth-token",
            "private-token",
            "access-token",
            "bearer",
        )

        fun validateParameters(parameters: ActionParameters): String? {
            val rawUrl = parameters.webhookUrl.trim()
            if (rawUrl.isBlank()) {
                return "Webhook URL cannot be empty"
            }

            val uri = try {
                URI(rawUrl)
            } catch (e: Exception) {
                return "Invalid Webhook URL format"
            }

            val scheme = uri.scheme?.lowercase()
            if (scheme != "https") {
                return "Webhook URL must use HTTPS scheme"
            }

            if (uri.host.isNullOrBlank()) {
                return "Webhook URL must contain a valid host"
            }

            val method = parameters.webhookMethod.trim().uppercase()
            if (method !in ALLOWED_METHODS) {
                return "Unsupported HTTP method: $method. Allowed: ${ALLOWED_METHODS.joinToString(", ")}"
            }

            val headerError = validateHeaders(parameters.webhookHeaders)
            if (headerError != null) {
                return headerError
            }

            return null
        }

        fun validateHeaders(rawHeaders: String): String? {
            if (rawHeaders.isBlank()) return null
            val lines = rawHeaders.split("\r\n", "\n", "\r")
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    return@forEachIndexed
                }
                if (line.contains('\r') || line.contains('\n')) {
                    return "Invalid header on line ${index + 1}: header cannot contain control characters"
                }
                val colonIdx = trimmed.indexOf(':')
                if (colonIdx <= 0) {
                    return "Invalid header format on line ${index + 1}: must be 'Name: Value'"
                }
                val name = trimmed.substring(0, colonIdx).trim()
                val value = trimmed.substring(colonIdx + 1).trim()
                if (name.isEmpty()) {
                    return "Invalid header name on line ${index + 1}: name cannot be empty"
                }
                if (name.any { it.isISOControl() } || value.any { it.isISOControl() }) {
                    return "Invalid header on line ${index + 1}: header cannot contain control characters"
                }
            }
            return null
        }

        fun parseHeaders(rawHeaders: String): Map<String, String> {
            if (rawHeaders.isBlank()) return emptyMap()
            val result = mutableMapOf<String, String>()
            rawHeaders.split("\r\n", "\n", "\r").forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val colonIdx = trimmed.indexOf(':')
                    if (colonIdx > 0) {
                        val key = trimmed.substring(0, colonIdx).trim()
                        val value = trimmed.substring(colonIdx + 1).trim()
                        if (key.isNotEmpty() && !key.any { it.isISOControl() } && !value.any { it.isISOControl() }) {
                            result[key] = value
                        }
                    }
                }
            }
            return result
        }

        fun sanitizeUrlForLogging(rawUrl: String): String {
            if (rawUrl.isBlank()) return rawUrl
            return try {
                val uri = URI(rawUrl.trim())
                val scheme = uri.scheme
                val host = uri.host
                if (scheme == null || host == null) {
                    return redactSensitiveText(rawUrl)
                }
                val port = if (uri.port != -1) ":${uri.port}" else ""
                val path = uri.rawPath ?: ""
                val query = uri.rawQuery
                val sanitizedQuery = if (query.isNullOrEmpty()) {
                    ""
                } else {
                    "?" + query.split("&").joinToString("&") { param ->
                        val parts = param.split("=", limit = 2)
                        val k = parts[0]
                        if (parts.size == 2) {
                            if (isSensitiveQueryKey(k)) "$k=[REDACTED]" else "$k=[REDACTED]"
                        } else {
                            k
                        }
                    }
                }
                val fragment = if (uri.rawFragment != null) "#[REDACTED]" else ""
                val userInfo = if (uri.rawUserInfo != null) "[REDACTED]@" else ""
                "$scheme://$userInfo$host$port$path$sanitizedQuery$fragment"
            } catch (_: Exception) {
                redactSensitiveText(rawUrl)
            }
        }

        private fun isSensitiveQueryKey(key: String): Boolean {
            val lower = key.lowercase().trim()
            return lower.contains("token") || lower.contains("key") || lower.contains("secret") || lower.contains("password") || lower.contains("auth") || lower.contains("sig")
        }

        fun isSensitiveHeader(name: String): Boolean {
            val lower = name.lowercase().trim()
            return SENSITIVE_HEADER_KEYS.any { lower == it || lower.contains("token") || lower.contains("secret") || lower.contains("auth") || lower.contains("key") || lower.contains("cookie") }
        }

        fun sanitizeHeadersForLogging(headers: Map<String, String>): Map<String, String> {
            return headers.mapValues { (k, v) ->
                if (isSensitiveHeader(k)) "[REDACTED]" else v
            }
        }

        fun redactSensitiveText(text: String): String {
            // Redact URIs with queries or credentials, Bearer tokens, passwords, keys in arbitrary error messages or URLs
            var redacted = text
            redacted = redacted.replace(Regex("(?i)(https?://)([^\\s:@]+:[^\\s:@]+@)", RegexOption.IGNORE_CASE), "$1[REDACTED]@")
            redacted = redacted.replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9_\\-\\.~+/]+=*", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            redacted = redacted.replace(Regex("(?i)(key|secret|token|password|auth|api_key|apikey|access_token)=([^&\\s]+)", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            redacted = redacted.replace(Regex("(?i)(Basic\\s+)[A-Za-z0-9+/=]+", RegexOption.IGNORE_CASE), "$1[REDACTED]")
            // Also redact query strings in any URL embedded in text if it has parameters
            redacted = redacted.replace(Regex("(?i)(https?://[^\\s?#]+)\\?([^\\s#]+)")) { matchResult ->
                val base = matchResult.groupValues[1]
                val query = matchResult.groupValues[2]
                val safeQuery = query.split("&").joinToString("&") { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2) "${parts[0]}=[REDACTED]" else parts[0]
                }
                "$base?$safeQuery"
            }
            return redacted
        }
    }
}
