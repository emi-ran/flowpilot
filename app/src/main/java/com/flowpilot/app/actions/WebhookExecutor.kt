package com.flowpilot.app.actions

import android.util.Log
import com.flowpilot.app.data.model.ActionType
import java.io.EOFException
import java.io.InputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Socket
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

/** HTTPS transport whose first TCP connection targets only prevalidated [address]. */
internal class PinnedHttpsTransport(
    private val url: URL,
    private val address: InetAddress,
    private val rawSocketFactory: () -> Socket = ::Socket,
    private val sslSocketFactory: SSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory(),
    private val verifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier(),
) : WebhookTransport {
    private var socket: SSLSocket? = null

    override fun execute(method: String, headers: Map<String, String>, body: ByteArray, timeoutMs: Int): Int {
        val tlsSocket = connect(timeoutMs)
        val requestTarget = url.file.takeIf { it.isNotEmpty() } ?: "/"
        val host = hostHeader(url)
        val request = buildString {
            append("$method $requestTarget HTTP/1.1\r\n")
            append("Host: $host\r\n")
            append("Connection: close\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            if (method in WebhookExecutor.METHODS_WITH_BODY) append("Content-Length: ${body.size}\r\n")
            append("\r\n")
        }
        tlsSocket.outputStream.write(request.toByteArray(StandardCharsets.ISO_8859_1))
        if (body.isNotEmpty()) tlsSocket.outputStream.write(body)
        tlsSocket.outputStream.flush()
        return readResponseCode(tlsSocket.inputStream)
    }

    override fun close() {
        try {
            socket?.close()
        } finally {
            socket = null
        }
    }

    private fun connect(timeoutMs: Int): SSLSocket {
        socket?.let { return it }
        val port = if (url.port == -1) DEFAULT_HTTPS_PORT else url.port
        val tcpSocket = rawSocketFactory()
        try {
            tcpSocket.connect(InetSocketAddress(address, port), timeoutMs)
            tcpSocket.soTimeout = timeoutMs
            val tlsSocket = sslSocketFactory.createSocket(tcpSocket, url.host, port, true) as SSLSocket
            tlsSocket.soTimeout = timeoutMs
            tlsSocket.startHandshake()
            if (!verifier.verify(url.host, tlsSocket.session)) {
                throw javax.net.ssl.SSLPeerUnverifiedException("HTTPS hostname verification failed")
            }
            socket = tlsSocket
            return tlsSocket
        } catch (e: Exception) {
            try {
                tcpSocket.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun readResponseCode(input: InputStream): Int {
        repeat(MAX_INTERIM_RESPONSES) {
            val statusLine = readLine(input)
            val parts = statusLine.split(' ', limit = 3)
            val status = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size < 2 || !parts[0].startsWith("HTTP/") || status == null || status !in 100..599) {
                throw ProtocolException("Invalid HTTPS response status")
            }
            consumeHeaders(input)
            if (status !in 100..199) return status
            if (status == SWITCHING_PROTOCOLS) throw ProtocolException("HTTPS protocol upgrade is not supported")
        }
        throw ProtocolException("Too many interim HTTPS responses")
    }

    private fun consumeHeaders(input: InputStream) {
        var consumed = 0
        while (true) {
            val line = readLine(input)
            consumed += line.length + CRLF_BYTES
            if (consumed > MAX_RESPONSE_HEADER_BYTES) throw ProtocolException("HTTPS response headers are too large")
            if (line.isEmpty()) return
        }
    }

    private fun readLine(input: InputStream): String {
        val bytes = ArrayList<Byte>(MAX_RESPONSE_LINE_BYTES)
        while (true) {
            val value = input.read()
            if (value == -1) throw EOFException("HTTPS response ended before headers completed")
            if (value == '\n'.code) {
                if (bytes.lastOrNull()?.toInt() != '\r'.code) throw ProtocolException("Invalid HTTPS response line ending")
                bytes.removeAt(bytes.lastIndex)
                return String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)
            }
            if (bytes.size >= MAX_RESPONSE_LINE_BYTES) throw ProtocolException("HTTPS response line is too large")
            bytes += value.toByte()
        }
    }

    private companion object {
        const val DEFAULT_HTTPS_PORT = 443
        const val CRLF_BYTES = 2
        const val MAX_RESPONSE_LINE_BYTES = 8 * 1024
        const val MAX_RESPONSE_HEADER_BYTES = 32 * 1024
        const val MAX_INTERIM_RESPONSES = 8
        const val SWITCHING_PROTOCOLS = 101

        fun hostHeader(url: URL): String {
            val host = url.host.let { if (':' in it && !it.startsWith("[")) "[$it]" else it }
            return host + if (url.port != -1 && url.port != DEFAULT_HTTPS_PORT) ":${url.port}" else ""
        }
    }
}

internal interface WebhookTransport {
    fun execute(method: String, headers: Map<String, String>, body: ByteArray, timeoutMs: Int): Int
    fun close()
}

/**
 * Executes outbound HTTPS webhook requests through a validated-address transport.
 * Validates URLs, bounds timeouts, handles headers/bodies, redacts secrets in logs and failure messages.
 */
class WebhookExecutor internal constructor(
    private val transportFactory: (URL, InetAddress) -> WebhookTransport = { url, address ->
        PinnedHttpsTransport(url, address)
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
        val headers = try {
            renderHeaders(parameters.webhookHeaders, parameters.webhookTemplateContext)
        } catch (e: IllegalArgumentException) {
            return ActionResult(false, e.message ?: "Invalid rendered webhook headers")
        }
        val body = WebhookTemplateRenderer.render(parameters.webhookBody, parameters.webhookTemplateContext)

        Log.i(TAG, "Dispatching HTTP Webhook: method=$method")

        var transport: WebhookTransport? = null
        return try {
            val url = URI(rawUrl).toURL()
            val address = validateResolvedAddresses(url.host)
            transport = transportFactory(url, address)
            val statusCode = transport.execute(
                method = method,
                headers = headers,
                body = if (method in METHODS_WITH_BODY) body.toByteArray(StandardCharsets.UTF_8) else ByteArray(0),
                timeoutMs = timeoutMs,
            )
            val isSuccess = statusCode in 200..299
            val message = if (isSuccess) {
                "HTTP Webhook delivered: status $statusCode"
            } else {
                "HTTP Webhook failed: status $statusCode"
            }
            Log.i(TAG, "HTTP Webhook result: status=$statusCode")
            ActionResult(isSuccess, message)
        } catch (e: Exception) {
            // Exception messages can echo arbitrary URLs, header values or body data.
            val safeMessage = if (e is SecurityException) {
                "Webhook destination rejected: non-public address or unsafe connection"
            } else {
                "Request could not be completed [REDACTED]"
            }
            Log.w(TAG, "HTTP Webhook execution failed")
            ActionResult(false, "HTTP request failed: $safeMessage")
        } finally {
            try {
                transport?.close()
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
                val allowedMethods = ALLOWED_METHODS.joinToString(separator = ", ")
                return "Unsupported HTTP method: $method. Allowed: $allowedMethods"
            }

            if (method !in METHODS_WITH_BODY && parameters.webhookBody.isNotEmpty()) {
                return "HTTP method $method does not support a webhook body"
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
                if (!name.all { it in HEADER_NAME_CHARS }) {
                    return "Invalid header name on line ${index + 1}"
                }
                if (name.lowercase() in FORBIDDEN_REQUEST_HEADERS) {
                    return "Forbidden header name on line ${index + 1}"
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

        /** Renders configured header lines independently; replacements may not add header lines. */
        fun renderHeaders(rawHeaders: String, context: WebhookTemplateContext?): Map<String, String> {
            val validationError = validateHeaders(rawHeaders)
            require(validationError == null) { validationError ?: "Invalid webhook headers" }
            if (rawHeaders.isBlank()) return emptyMap()

            return buildMap {
                rawHeaders.split("\r\n", "\n", "\r").forEachIndexed { index, line ->
                    val configuredLine = line.trim()
                    if (configuredLine.isEmpty() || configuredLine.startsWith("#")) return@forEachIndexed
                    val renderedLine = WebhookTemplateRenderer.render(configuredLine, context)
                    if (renderedLine.any { it == '\r' || it == '\n' || it.isISOControl() }) {
                        throw IllegalArgumentException("Invalid rendered header on line ${index + 1}: header cannot contain control characters")
                    }
                    val colonIndex = renderedLine.indexOf(':')
                    val name = renderedLine.substring(0, colonIndex).trim()
                    val value = renderedLine.substring(colonIndex + 1).trim()
                    if (!name.all { it in HEADER_NAME_CHARS }) {
                        throw IllegalArgumentException("Invalid rendered header name on line ${index + 1}")
                    }
                    if (name.lowercase() in FORBIDDEN_REQUEST_HEADERS) {
                        throw IllegalArgumentException("Forbidden rendered header name on line ${index + 1}")
                    }
                    put(name, value)
                }
            }
        }

        private const val HEADER_NAME_CHARS = "!#$%&'*+-.^_`|~0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val FORBIDDEN_REQUEST_HEADERS = setOf(
            "connection", "content-length", "host", "keep-alive", "proxy-connection",
            "expect", "proxy-authenticate", "proxy-authorization", "te", "trailer",
            "transfer-encoding", "upgrade",
        )

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
                            "$k=[REDACTED]"
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
