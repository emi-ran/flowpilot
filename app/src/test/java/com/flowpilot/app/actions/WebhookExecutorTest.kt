package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ProtocolException
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.lang.reflect.Proxy
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebhookExecutorTest {

    @Test
    fun execute_pinsSingleValidatedAddress_andClosesTransport() {
        val validated = InetAddress.getByName("93.184.216.34")
        val transport = FakeTransport(204)
        var lookups = 0
        var suppliedAddress: InetAddress? = null
        val result = WebhookExecutor(
            transportFactory = { _, address -> suppliedAddress = address; transport },
            addressLookup = { if (++lookups == 1) arrayOf(validated) else arrayOf(InetAddress.getByName("10.0.0.1")) },
        ).execute(ActionType.HTTP_WEBHOOK, ActionParameters(webhookUrl = "https://target.example/hook"))

        assertThat(result.success).isTrue()
        assertThat(lookups).isEqualTo(1)
        assertThat(suppliedAddress).isEqualTo(validated)
        assertThat(transport.closed).isTrue()
    }

    @Test
    fun execute_rejectsNonPublicAddressAndRenderedHeaderInjection_beforeTransport() {
        var opened = false
        val privateTarget = WebhookExecutor(
            transportFactory = { _, _ -> opened = true; error("must not connect") },
            addressLookup = { arrayOf(InetAddress.getByName("127.0.0.1")) },
        ).execute(ActionType.HTTP_WEBHOOK, ActionParameters(webhookUrl = "https://target.example/hook"))
        assertThat(privateTarget.success).isFalse()
        assertThat(opened).isFalse()

        val injected = WebhookExecutor(
            transportFactory = { _, _ -> opened = true; error("must not connect") },
            addressLookup = { arrayOf(InetAddress.getByName("93.184.216.34")) },
        ).execute(ActionType.HTTP_WEBHOOK, ActionParameters(
            webhookUrl = "https://target.example/hook",
            webhookHeaders = "X-Trigger: \${trigger}",
            webhookTemplateContext = WebhookTemplateContext(trigger = "safe\r\nAuthorization: attacker"),
        ))
        assertThat(injected.success).isFalse()
        assertThat(injected.message).doesNotContain("attacker")
        assertThat(opened).isFalse()
    }

    @Test
    fun execute_rejectsReservedHeadersAndBodiesForBodylessMethods() {
        listOf(
            "Host: attacker.example", "Transfer-Encoding: chunked", "Connection: keep-alive",
            "Expect: 100-continue", "Proxy-Authorization: Basic attacker",
        ).forEach {
            assertThat(WebhookExecutor.validateHeaders(it)).contains("Forbidden header")
        }
        val result = WebhookExecutor().execute(ActionType.HTTP_WEBHOOK, ActionParameters(
            webhookUrl = "https://target.example/hook",
            webhookMethod = "GET",
            webhookBody = "must-not-send",
        ))
        assertThat(result.success).isFalse()
        assertThat(result.message).contains("does not support a webhook body")
    }

    @Test
    fun execute_sendsPostBodyHeadersAndTimeout_toTransport() {
        val transport = FakeTransport(200)
        val result = WebhookExecutor(
            transportFactory = { _, _ -> transport },
            addressLookup = { arrayOf(InetAddress.getByName("93.184.216.34")) },
        ).execute(ActionType.HTTP_WEBHOOK, ActionParameters(
            webhookUrl = "https://target.example/hook",
            webhookMethod = "POST",
            webhookHeaders = "Content-Type: application/json\nX-Event: \${trigger}",
            webhookBody = "{\"event\":\"\${trigger}\"}",
            webhookTimeoutSeconds = 5,
            webhookTemplateContext = WebhookTemplateContext(trigger = "CHARGER_CONNECTED"),
        ))

        assertThat(result.success).isTrue()
        assertThat(transport.method).isEqualTo("POST")
        assertThat(transport.headers).containsEntry("X-Event", "CHARGER_CONNECTED")
        assertThat(String(transport.body, StandardCharsets.UTF_8)).isEqualTo("{\"event\":\"CHARGER_CONNECTED\"}")
        assertThat(transport.timeoutMs).isEqualTo(5_000)
        assertThat(transport.closed).isTrue()
    }

    @Test
    fun execute_closesTransportWhenDispatchFails() {
        val transport = object : WebhookTransport {
            var closed = false
            override fun execute(method: String, headers: Map<String, String>, body: ByteArray, timeoutMs: Int): Int =
                throw ProtocolException("synthetic")
            override fun close() { closed = true }
        }
        val result = WebhookExecutor(
            transportFactory = { _, _ -> transport },
            addressLookup = { arrayOf(InetAddress.getByName("93.184.216.34")) },
        ).execute(ActionType.HTTP_WEBHOOK, ActionParameters(webhookUrl = "https://target.example/hook"))

        assertThat(result.success).isFalse()
        assertThat(transport.closed).isTrue()
    }

    @Test
    fun pinnedTransport_serializesFixedLengthRequest_andSkipsInterimResponse() {
        val rawSocket = RecordingSocket()
        val tlsSocket = RecordingTlsSocket("HTTP/1.1 100 Continue\r\nX-Interim: yes\r\n\r\nHTTP/1.1 204 No Content\r\nX-Final: yes\r\n\r\n")
        val tlsFactory = RecordingSslSocketFactory(tlsSocket)
        val transport = PinnedHttpsTransport(
            URL("https://original.example:8443/hook?x=1"),
            InetAddress.getByName("93.184.216.34"),
            rawSocketFactory = { rawSocket },
            sslSocketFactory = tlsFactory,
            verifier = HostnameVerifier { host, _ -> host == "original.example" },
        )

        val status = transport.execute("POST", mapOf("X-Test" to "yes"), "body".toByteArray(), 5_000)

        assertThat(status).isEqualTo(204)
        assertThat(rawSocket.address).isEqualTo(InetAddress.getByName("93.184.216.34"))
        assertThat(rawSocket.port).isEqualTo(8443)
        assertThat(tlsFactory.layeredHost).isEqualTo("original.example")
        assertThat(tlsSocket.written.toString(StandardCharsets.ISO_8859_1.name())).isEqualTo(
            "POST /hook?x=1 HTTP/1.1\r\nHost: original.example:8443\r\nConnection: close\r\nX-Test: yes\r\nContent-Length: 4\r\n\r\nbody",
        )
        transport.close()
        assertThat(tlsSocket.closed).isTrue()
    }

    @Test
    fun pinnedTransport_rejectsMalformedResponseLine() {
        val transport = PinnedHttpsTransport(
            URL("https://original.example/hook"),
            InetAddress.getByName("93.184.216.34"),
            rawSocketFactory = { RecordingSocket() },
            sslSocketFactory = RecordingSslSocketFactory(RecordingTlsSocket("not-http\n")),
            verifier = HostnameVerifier { _, _ -> true },
        )
        try {
            transport.execute("GET", emptyMap(), ByteArray(0), 5_000)
            throw AssertionError("malformed response must fail")
        } catch (_: ProtocolException) {
        }
    }

    @Test
    fun pinnedTransport_rejectsHostnameMismatch_andClosesTcpSocket() {
        val rawSocket = RecordingSocket()
        val transport = PinnedHttpsTransport(
            URL("https://original.example/hook"),
            InetAddress.getByName("93.184.216.34"),
            rawSocketFactory = { rawSocket },
            sslSocketFactory = RecordingSslSocketFactory(RecordingTlsSocket("")),
            verifier = HostnameVerifier { _, _ -> false },
        )

        try {
            transport.execute("GET", emptyMap(), ByteArray(0), 5_000)
            throw AssertionError("hostname mismatch must fail")
        } catch (_: SSLPeerUnverifiedException) {
            assertThat(rawSocket.closed).isTrue()
        }
    }

    private class FakeTransport(private val responseCode: Int) : WebhookTransport {
        var method = ""
        var headers = emptyMap<String, String>()
        var body = ByteArray(0)
        var timeoutMs = 0
        var closed = false
        override fun execute(method: String, headers: Map<String, String>, body: ByteArray, timeoutMs: Int): Int {
            this.method = method; this.headers = headers; this.body = body; this.timeoutMs = timeoutMs
            return responseCode
        }
        override fun close() { closed = true }
    }

    private class RecordingSocket : Socket() {
        var address: InetAddress? = null
        var port: Int? = null
        var closed = false
        override fun connect(endpoint: java.net.SocketAddress?, timeout: Int) {
            (endpoint as java.net.InetSocketAddress).also { address = it.address; port = it.port }
        }
        override fun setSoTimeout(timeout: Int) = Unit
        override fun close() { closed = true }
    }

    private class RecordingSslSocketFactory(private val socket: SSLSocket) : SSLSocketFactory() {
        var layeredHost: String? = null
        override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
            layeredHost = host
            return this.socket
        }
        override fun createSocket(): Socket = error("unexpected")
        override fun createSocket(host: String, port: Int): Socket = error("unexpected")
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = error("unexpected")
        override fun createSocket(address: InetAddress, port: Int): Socket = error("unexpected")
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = error("unexpected")
        override fun getDefaultCipherSuites(): Array<String> = emptyArray()
        override fun getSupportedCipherSuites(): Array<String> = emptyArray()
    }

    private class RecordingTlsSocket(response: String) : SSLSocket() {
        val written = ByteArrayOutputStream()
        val input = ByteArrayInputStream(response.toByteArray(StandardCharsets.ISO_8859_1))
        var closed = false
        override fun getOutputStream() = written
        override fun getInputStream() = input
        override fun close() { closed = true }
        override fun setSoTimeout(timeout: Int) = Unit
        override fun getSupportedCipherSuites(): Array<String> = emptyArray()
        override fun getEnabledCipherSuites(): Array<String> = emptyArray()
        override fun setEnabledCipherSuites(suites: Array<out String>?) = Unit
        override fun getSupportedProtocols(): Array<String> = emptyArray()
        override fun getEnabledProtocols(): Array<String> = emptyArray()
        override fun setEnabledProtocols(protocols: Array<out String>?) = Unit
        override fun getSession(): SSLSession = Proxy.newProxyInstance(SSLSession::class.java.classLoader, arrayOf(SSLSession::class.java)) { _, _, _ -> null } as SSLSession
        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener?) = Unit
        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener?) = Unit
        override fun startHandshake() = Unit
        override fun setUseClientMode(mode: Boolean) = Unit
        override fun getUseClientMode(): Boolean = true
        override fun setNeedClientAuth(need: Boolean) = Unit
        override fun getNeedClientAuth(): Boolean = false
        override fun setWantClientAuth(want: Boolean) = Unit
        override fun getWantClientAuth(): Boolean = false
        override fun setEnableSessionCreation(flag: Boolean) = Unit
        override fun getEnableSessionCreation(): Boolean = true
    }
}
