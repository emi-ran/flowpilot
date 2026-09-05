package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebhookPrivacyRegressionTest {
    @Test
    fun dispatchAndResultLogsContainOnlyMethodAndStatus() {
        ShadowLog.clear()
        val executor = WebhookExecutor(
            connectionFactory = { url, _ -> object : HttpURLConnection(url) {
                override fun connect() = Unit
                override fun disconnect() = Unit
                override fun usingProxy() = false
                override fun getResponseCode() = 204
            } },
            addressLookup = { arrayOf(InetAddress.getByName("93.184.216.34")) },
        )
        val result = executor.execute(ActionType.HTTP_WEBHOOK, ActionParameters(
            webhookUrl = "https://private-host.example/path-secret?bare-secret&query-secret=value-secret#fragment-secret",
            webhookMethod = "GET",
            webhookHeaders = "X-Custom: header-secret\nContent-Type: type-secret",
        ))
        assertThat(result.success).isTrue()
        assertThat(ShadowLog.getLogsForTag(WebhookExecutor.TAG).map { it.msg }).containsExactly(
            "Dispatching HTTP Webhook: method=GET",
            "HTTP Webhook result: status=204",
        ).inOrder()
    }

    @Test
    fun exceptionTextNeverEntersLogsOrFailureResult() {
        ShadowLog.clear()
        val secret = "https://private-host.example/path-secret?bare-secret X-Custom: header-secret"
        val executor = WebhookExecutor(
            connectionFactory = { _, _ -> throw IOException(secret) },
            addressLookup = { arrayOf(InetAddress.getByName("93.184.216.34")) },
        )
        val result = executor.execute(ActionType.HTTP_WEBHOOK, ActionParameters(
            webhookUrl = "https://target.example/path-secret",
            webhookMethod = "GET",
        ))
        assertThat(result.success).isFalse()
        assertThat(result.message).doesNotContain("path-secret")
        assertThat(result.message).doesNotContain("header-secret")
        assertThat(ShadowLog.getLogsForTag(WebhookExecutor.TAG).map { it.msg }).containsExactly(
            "Dispatching HTTP Webhook: method=GET",
            "HTTP Webhook execution failed",
        ).inOrder()
        assertThat(ShadowLog.getLogsForTag(WebhookExecutor.TAG).all { it.throwable == null }).isTrue()
    }
}
