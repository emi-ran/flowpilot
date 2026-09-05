package com.flowpilot.app.actions

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsExecutorTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
    }

    @Test
    fun sendSms_fails_whenRecipientIsBlank() {
        val executor = SmsExecutor(app)

        val result = executor.execute(
            ActionType.SEND_SMS,
            ActionParameters(smsRecipient = "  ", smsMessage = "Hello")
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Recipient phone number is required")
    }

    @Test
    fun sendSms_fails_whenMessageIsBlank() {
        val executor = SmsExecutor(app)

        val result = executor.execute(
            ActionType.SEND_SMS,
            ActionParameters(smsRecipient = "+905551234567", smsMessage = "  ")
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("SMS message body cannot be empty")
    }

    @Test
    fun sendSms_fails_whenPermissionNotGranted() {
        val executor = SmsExecutor(app)

        val result = executor.execute(
            ActionType.SEND_SMS,
            ActionParameters(smsRecipient = "+905551234567", smsMessage = "Hello World")
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("SEND_SMS permission required")
    }

    @Test
    fun sendSms_hidesProviderExceptionDetails() {
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS)
        val secret = "provider-token=https://sms.example.test/send?apiKey=secret123 +905551234567"
        val executor = SmsExecutor(
            context = app,
            sendTextMessage = { _, _ -> throw IllegalStateException(secret) }
        )

        val result = executor.execute(
            ActionType.SEND_SMS,
            ActionParameters(smsRecipient = "+905****4567", smsMessage = "Hello World")
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Failed to send SMS")
        assertThat(result.message).doesNotContain(secret)
    }

    @Test
    fun sendSms_sendsMessage_whenPermissionGranted() {
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS)
        var capturedRecipient: String? = null
        var capturedMessage: String? = null

        val executor = SmsExecutor(
            context = app,
            sendTextMessage = { r, m ->
                capturedRecipient = r
                capturedMessage = m
            }
        )

        val result = executor.execute(
            ActionType.SEND_SMS,
            ActionParameters(smsRecipient = "+905551234567", smsMessage = "Hello World")
        )

        assertThat(result.success).isTrue()
        assertThat(capturedRecipient).isEqualTo("+905551234567")
        assertThat(capturedMessage).isEqualTo("Hello World")
    }

    @Test
    fun sendSms_interpolatesTemplateVariables() {
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS)
        var capturedRecipient: String? = null
        var capturedMessage: String? = null

        val executor = SmsExecutor(
            context = app,
            sendTextMessage = { r, m ->
                capturedRecipient = r
                capturedMessage = m
            }
        )

        val templateContext = WebhookTemplateContext(
            smsSender = "+905559876543",
            smsOtp = "123456",
        )

        val result = executor.execute(
            ActionType.SEND_SMS,
            ActionParameters(
                smsRecipient = "\${sms.sender}",
                smsMessage = "Your OTP code is \${sms.otp}",
                webhookTemplateContext = templateContext
            )
        )

        assertThat(result.success).isTrue()
        assertThat(capturedRecipient).isEqualTo("+905559876543")
        assertThat(capturedMessage).isEqualTo("Your OTP code is 123456")
    }

    @Test
    fun draftSms_createsCorrectSendToIntent() {
        lateinit var launchedIntent: Intent
        val executor = SmsExecutor(
            context = app,
            resolveActivity = { true },
            startActivity = { launchedIntent = it }
        )

        val result = executor.execute(
            ActionType.DRAFT_SMS,
            ActionParameters(
                smsRecipient = "+905551234567",
                smsMessage = "Draft message content"
            )
        )

        assertThat(result.success).isTrue()
        assertThat(launchedIntent.action).isEqualTo(Intent.ACTION_SENDTO)
        assertThat(launchedIntent.data).isEqualTo(Uri.parse("smsto:%2B905551234567"))
        assertThat(launchedIntent.getStringExtra("sms_body")).isEqualTo("Draft message content")
    }

    @Test
    fun draftSms_returnsFailure_whenNoMessagingAppFound() {
        var started = false
        val executor = SmsExecutor(
            context = app,
            resolveActivity = { false },
            startActivity = { started = true }
        )

        val result = executor.execute(
            ActionType.DRAFT_SMS,
            ActionParameters(smsRecipient = "+905551234567", smsMessage = "Draft message")
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("No messaging application found")
        assertThat(started).isFalse()
    }
}
