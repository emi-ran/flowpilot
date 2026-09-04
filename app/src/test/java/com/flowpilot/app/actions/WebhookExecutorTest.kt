package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebhookExecutorTest {

    @Test
    fun execute_unsupportedAction_returnsFailure() {
        val executor = WebhookExecutor()
        val result = executor.execute(ActionType.VIBRATE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported action for Webhook")
    }

    @Test
    fun execute_emptyUrl_returnsFailure() {
        val executor = WebhookExecutor()
        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(webhookUrl = ""),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Webhook URL cannot be empty")
    }

    @Test
    fun execute_invalidUrlScheme_returnsFailure() {
        val executor = WebhookExecutor()
        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(webhookUrl = "ftp://example.com/api"),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("must use HTTPS scheme")
    }

    @Test
    fun execute_httpUrl_returnsFailureBeforeConnection() {
        var connectionFactoryCalled = false
        val executor = WebhookExecutor(connectionFactory = {
            connectionFactoryCalled = true
            error("HTTP webhook must not open connection")
        })

        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(webhookUrl = "http://example.com/api"),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("must use HTTPS scheme")
        assertThat(connectionFactoryCalled).isFalse()
    }

    @Test
    fun execute_unsupportedMethod_returnsFailure() {
        val executor = WebhookExecutor()
        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(
                webhookUrl = "https://example.com/api",
                webhookMethod = "INVALID_METHOD",
            ),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Unsupported HTTP method")
    }

    @Test
    fun execute_rendersTemplateVariablesInHeadersAndBody() {
        val mockConnection = FakeHttpURLConnection(URL("https://example.com/webhook"), 200)
        val executor = WebhookExecutor(connectionFactory = { mockConnection })
        val templateContext = WebhookTemplateContext(
            trigger = "CHARGER_CONNECTED",
            timestamp = 1700000000000L,
            timeProvider = { "2023-11-14T22:13:20Z" },
            batteryPercent = 90,
            isCharging = true,
            wifiSsid = "OfficeNet",
        )

        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(
                webhookUrl = "https://example.com/webhook",
                webhookMethod = "POST",
                webhookHeaders = "X-Trigger: \${trigger}\nX-Battery: \${batteryPercent}\nContent-Type: application/json",
                webhookBody = "{\"event\": \"\${trigger}\", \"time\": \"\${time}\", \"wifi\": \"\${wifiSsid}\", \"charging\": \${isCharging}}",
                webhookTemplateContext = templateContext,
            ),
        )

        assertThat(result.success).isTrue()
        assertThat(mockConnection.recordedRequestProperties["X-Trigger"]).isEqualTo("CHARGER_CONNECTED")
        assertThat(mockConnection.recordedRequestProperties["X-Battery"]).isEqualTo("90")
        assertThat(mockConnection.recordedRequestProperties["Content-Type"]).isEqualTo("application/json")
        assertThat(mockConnection.writtenBody()).isEqualTo("{\"event\": \"CHARGER_CONNECTED\", \"time\": \"2023-11-14T22:13:20Z\", \"wifi\": \"OfficeNet\", \"charging\": true}")
    }

    @Test
    fun execute_successful200Response_returnsSuccess() {
        val mockConnection = FakeHttpURLConnection(URL("https://example.com/webhook"), 200)
        val executor = WebhookExecutor(connectionFactory = { mockConnection })

        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(
                webhookUrl = "https://example.com/webhook",
                webhookMethod = "POST",
                webhookHeaders = "Content-Type: application/json\nAuthorization: Bearer secret_token_123",
                webhookBody = "{\"state\": \"on\"}",
                webhookTimeoutSeconds = 5,
            ),
        )

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("status 200")
        assertThat(mockConnection.requestMethod).isEqualTo("POST")
        assertThat(mockConnection.connectTimeout).isEqualTo(5000)
        assertThat(mockConnection.readTimeout).isEqualTo(5000)
        assertThat(mockConnection.recordedRequestProperties["Content-Type"]).isEqualTo("application/json")
        assertThat(mockConnection.recordedRequestProperties["Authorization"]).isEqualTo("Bearer secret_token_123")
        assertThat(mockConnection.writtenBody()).isEqualTo("{\"state\": \"on\"}")
        assertThat(mockConnection.isDisconnected).isTrue()
    }

    @Test
    fun execute_204NoContent_returnsSuccess() {
        val mockConnection = FakeHttpURLConnection(URL("https://example.com/webhook"), 204)
        val executor = WebhookExecutor(connectionFactory = { mockConnection })

        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(
                webhookUrl = "https://example.com/webhook",
                webhookMethod = "GET",
            ),
        )

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("status 204")
    }

    @Test
    fun execute_http400Response_returnsFailure() {
        val mockConnection = FakeHttpURLConnection(URL("https://example.com/webhook"), 400)
        val executor = WebhookExecutor(connectionFactory = { mockConnection })

        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(
                webhookUrl = "https://example.com/webhook",
                webhookMethod = "POST",
            ),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("status 400")
    }

    @Test
    fun execute_http500Response_returnsFailure() {
        val mockConnection = FakeHttpURLConnection(URL("https://example.com/webhook"), 500)
        val executor = WebhookExecutor(connectionFactory = { mockConnection })

        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(
                webhookUrl = "https://example.com/webhook",
                webhookMethod = "POST",
            ),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("status 500")
    }

    @Test
    fun execute_networkExceptionWithSensitiveAuth_redactsAuthInFailureMessage() {
        val executor = WebhookExecutor(connectionFactory = {
            throw IOException("Failed to connect with Authorization: Bearer my_super_secret_token and key=secret123")
        })

        val result = executor.execute(
            ActionType.HTTP_WEBHOOK,
            ActionParameters(
                webhookUrl = "https://example.com/webhook",
                webhookMethod = "POST",
            ),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).doesNotContain("my_super_secret_token")
        assertThat(result.message).doesNotContain("secret123")
        assertThat(result.message).contains("[REDACTED]")
    }

    @Test
    fun parseHeaders_handlesValidLinesAndComments() {
        val raw = """
            Content-Type: application/json
            # Comment line
            Authorization: Bearer test_token
            X-Custom-Header: value with : colon
            EmptyValue:
        """.trimIndent()

        val headers = WebhookExecutor.parseHeaders(raw)
        assertThat(headers).hasSize(4)
        assertThat(headers["Content-Type"]).isEqualTo("application/json")
        assertThat(headers["Authorization"]).isEqualTo("Bearer test_token")
        assertThat(headers["X-Custom-Header"]).isEqualTo("value with : colon")
        assertThat(headers["EmptyValue"]).isEqualTo("")
    }

    @Test
    fun sanitizeHeadersForLogging_redactsSensitiveHeaders() {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer sensitive_token",
            "X-Api-Key" to "api_key_value",
            "Cookie" to "session=abc",
            "User-Agent" to "FlowPilot",
        )

        val sanitized = WebhookExecutor.sanitizeHeadersForLogging(headers)

        assertThat(sanitized["Content-Type"]).isEqualTo("application/json")
        assertThat(sanitized["User-Agent"]).isEqualTo("FlowPilot")
        assertThat(sanitized["Authorization"]).isEqualTo("[REDACTED]")
        assertThat(sanitized["X-Api-Key"]).isEqualTo("[REDACTED]")
        assertThat(sanitized["Cookie"]).isEqualTo("[REDACTED]")
    }

    @Test
    fun redactSensitiveText_replacesTokensAndPasswords() {
        val text = "Error: Bearer 1234567890abcdef failed. Param token=sensitive_tok&user=bob, password=mypassword https://api.example.com/v1/trigger?token=secret123&user=admin"
        val redacted = WebhookExecutor.redactSensitiveText(text)

        assertThat(redacted).doesNotContain("1234567890abcdef")
        assertThat(redacted).doesNotContain("sensitive_tok")
        assertThat(redacted).doesNotContain("mypassword")
        assertThat(redacted).doesNotContain("secret123")
        assertThat(redacted).doesNotContain("admin")
        assertThat(redacted).contains("Bearer [REDACTED]")
        assertThat(redacted).contains("token=[REDACTED]")
        assertThat(redacted).contains("password=[REDACTED]")
        assertThat(redacted).contains("https://api.example.com/v1/trigger?token=[REDACTED]&user=[REDACTED]")
    }

    @Test
    fun sanitizeUrlForLogging_redactsQueryParamsAndUserInfo() {
        val url = "https://user:pass123@api.example.com/v1/webhook?apiKey=xyz789&action=alert"
        val sanitized = WebhookExecutor.sanitizeUrlForLogging(url)

        assertThat(sanitized).doesNotContain("pass123")
        assertThat(sanitized).doesNotContain("xyz789")
        assertThat(sanitized).contains("https://[REDACTED]@api.example.com/v1/webhook?apiKey=[REDACTED]&action=[REDACTED]")
    }

    @Test
    fun validateParameters_rejectsMalformedHeaders() {
        val invalidHeaderParam = ActionParameters(
            webhookUrl = "https://example.com/webhook",
            webhookHeaders = "InvalidHeaderWithoutColon",
        )
        val error = WebhookExecutor.validateParameters(invalidHeaderParam)
        assertThat(error).isNotNull()
        assertThat(error).contains("Invalid header format on line 1")
        // Ensure failure message does not echo raw header value
        assertThat(error).doesNotContain("InvalidHeaderWithoutColon")
    }

    @Test
    fun validateParameters_rejectsCrlfInHeader() {
        val crlfHeaderParam = ActionParameters(
            webhookUrl = "https://example.com/webhook",
            webhookHeaders = "X-Bad-Header: value\u0000injection",
        )
        val error = WebhookExecutor.validateParameters(crlfHeaderParam)
        assertThat(error).isNotNull()
        assertThat(error).contains("header cannot contain control characters")
        assertThat(error).doesNotContain("value\u0000injection")
    }

    @Test
    fun validateHeaders_validatesCorrectly() {
        assertThat(WebhookExecutor.validateHeaders("")).isNull()
        assertThat(WebhookExecutor.validateHeaders("  \n  ")).isNull()
        assertThat(WebhookExecutor.validateHeaders("# Just comment\nContent-Type: application/json")).isNull()
        
        val emptyNameError = WebhookExecutor.validateHeaders(": value_without_name")
        assertThat(emptyNameError).contains("Invalid header format on line 1")

        val malformedError = WebhookExecutor.validateHeaders("MalformedHeader")
        assertThat(malformedError).contains("Invalid header format on line 1")
    }

    private class FakeHttpURLConnection(url: URL, private val responseCodeStub: Int) : HttpURLConnection(url) {
        val recordedRequestProperties = mutableMapOf<String, String>()
        private val outputStreamBuffer = ByteArrayOutputStream()
        var isDisconnected = false

        override fun setRequestProperty(key: String, value: String) {
            recordedRequestProperties[key] = value
        }

        override fun getOutputStream(): java.io.OutputStream = outputStreamBuffer

        override fun getResponseCode(): Int = responseCodeStub

        override fun connect() {}

        override fun disconnect() {
            isDisconnected = true
        }

        override fun usingProxy(): Boolean = false

        fun writtenBody(): String = outputStreamBuffer.toString(StandardCharsets.UTF_8.name())
    }
}
